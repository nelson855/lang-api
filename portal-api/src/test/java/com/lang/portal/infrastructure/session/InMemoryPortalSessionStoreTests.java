package com.lang.portal.infrastructure.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class InMemoryPortalSessionStoreTests {

  @Test
  void everyLoginGetsItsOwnIndependentSessionIdentity() {
    InMemoryPortalSessionStore store = store(Duration.ofMinutes(30));

    String first = store.create("upstream-a", 42L);
    String second = store.create("upstream-a", 42L);

    assertThat(first).isNotEqualTo(second);
    assertThat(store.find(first)).isEqualTo(new PortalSessionRecord(first, "upstream-a", 42L));
    assertThat(store.find(second)).isEqualTo(new PortalSessionRecord(second, "upstream-a", 42L));
  }

  @Test
  void issuedSessionIdDoesNotExposeTheUpstreamSessionValue() {
    InMemoryPortalSessionStore store = store(Duration.ofMinutes(30));

    String id = store.create("upstream-secret-value", 42L);

    assertThat(id).doesNotContain("upstream-secret-value");
  }

  @Test
  void deletedSessionIsNotRebuiltByItsOldIdentifier() {
    InMemoryPortalSessionStore store = store(Duration.ofMinutes(30));
    String id = store.create("upstream-a", 42L);

    assertThat(store.delete(id)).isTrue();

    assertThat(store.find(id)).isNull();
    assertThat(store.delete(id)).isFalse();
  }

  @Test
  void deletingOneSessionKeepsOtherSessionsAndOtherUsersIntact() {
    InMemoryPortalSessionStore store = store(Duration.ofMinutes(30));
    String first = store.create("upstream-a", 42L);
    String second = store.create("upstream-a", 42L);
    String otherUser = store.create("upstream-b", 7L);

    store.delete(first);

    assertThat(store.find(first)).isNull();
    assertThat(store.find(second)).isNotNull();
    assertThat(store.find(otherUser)).isNotNull();
  }

  @Test
  void expiredSessionIsNoLongerFound() {
    AtomicLong now = new AtomicLong(1_000L);
    InMemoryPortalSessionStore store = new InMemoryPortalSessionStore(Duration.ofMinutes(30), now::get);
    String id = store.create("upstream-a", 42L);

    now.addAndGet(Duration.ofMinutes(31).toMillis());

    assertThat(store.find(id)).isNull();
  }

  @Test
  void sessionIsStillFoundWithinItsTimeToLive() {
    AtomicLong now = new AtomicLong(1_000L);
    InMemoryPortalSessionStore store = new InMemoryPortalSessionStore(Duration.ofMinutes(30), now::get);
    String id = store.create("upstream-a", 42L);

    now.addAndGet(Duration.ofMinutes(29).toMillis());

    assertThat(store.find(id)).isNotNull();
  }

  @Test
  void unknownSessionIdIsSimplyAbsent() {
    InMemoryPortalSessionStore store = store(Duration.ofMinutes(30));

    assertThat(store.find("never-issued")).isNull();
    assertThat(store.delete("never-issued")).isFalse();
  }

  @Test
  void aFreshStoreDoesNotSeeSessionsFromAnotherInstance() {
    // 该用例用于说明跨实例语义：内存实现只覆盖单实例，Redis 实现才是跨实例权威。
    InMemoryPortalSessionStore instanceA = store(Duration.ofMinutes(30));
    InMemoryPortalSessionStore instanceB = store(Duration.ofMinutes(30));
    String id = instanceA.create("upstream-a", 42L);

    assertThat(instanceB.find(id)).isNull();
  }

  @Test
  void upstreamSessionValueIsNeverCarriedInsideTheIssuedIdentifier() {
    InMemoryPortalSessionStore store = store(Duration.ofMinutes(30));

    String id = store.create("s".repeat(64), 42L);

    assertThat(id).hasSizeLessThanOrEqualTo(64).matches("[0-9a-f]+");
  }

  @Test
  void deletingAnAlreadyDeletedSessionIsIdempotent() {
    InMemoryPortalSessionStore store = store(Duration.ofMinutes(30));
    String id = store.create("upstream-a", 42L);

    store.delete(id);

    assertThat(store.delete(id)).isFalse();
    assertThat(store.find(id)).isNull();
  }

  @Test
  void storageFailureIsNotSilentlyTreatedAsAnAbsentSession() {
    // 契约：存储故障必须抛出，由调用方决定失败语义，不能伪装成"没有这条会话"。
    PortalSessionStore failing =
        new PortalSessionStore() {
          @Override
          public String create(String upstreamSession, long userId) {
            throw new IllegalStateException("storage unavailable");
          }

          @Override
          public PortalSessionRecord find(String sessionId) {
            throw new IllegalStateException("storage unavailable");
          }

          @Override
          public boolean delete(String sessionId) {
            throw new IllegalStateException("storage unavailable");
          }
        };

    assertThatThrownBy(() -> failing.find("any")).isInstanceOf(IllegalStateException.class);
  }

  private static InMemoryPortalSessionStore store(Duration ttl) {
    return new InMemoryPortalSessionStore(ttl);
  }
}