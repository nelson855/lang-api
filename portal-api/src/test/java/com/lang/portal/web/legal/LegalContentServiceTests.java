package com.lang.portal.web.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.LegalContentFormat;
import com.lang.portal.config.LegalContentProperties;
import com.lang.portal.upstream.newapi.legal.NewApiLegalContentSource;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LegalContentServiceTests {

  @Test
  void cachesSuccessfulSanitizedDocumentsPerLegalType() {
    AtomicLong now = new AtomicLong();
    CountingSource source = new CountingSource("<p>terms</p>", "<p>privacy</p><script>raw-secret</script>");
    LegalContentService service = service(source, now);

    assertThat(service.document(LegalContentType.TERMS).contentHtml()).isEqualTo("<p>terms</p>");
    assertThat(service.document(LegalContentType.TERMS).contentHtml()).isEqualTo("<p>terms</p>");
    assertThat(service.document(LegalContentType.PRIVACY).contentHtml()).isEqualTo("<p>privacy</p>");

    assertThat(source.termsCalls.get()).isOne();
    assertThat(source.privacyCalls.get()).isOne();
  }

  @Test
  void reloadsAfterTtlAndTreatsWithdrawnContentAsNotPublished() {
    AtomicLong now = new AtomicLong();
    CountingSource source = new CountingSource("<p>terms</p>", "<p>privacy</p>");
    LegalContentService service = service(source, now);
    service.document(LegalContentType.TERMS);
    source.terms = " ";
    now.addAndGet(Duration.ofSeconds(11).toMillis());

    assertThatThrownBy(() -> service.document(LegalContentType.TERMS))
        .isInstanceOf(PortalException.class)
        .matches(error -> ((PortalException) error).errorCode() == PortalErrorCode.NOT_FOUND);
    assertThat(source.termsCalls.get()).isEqualTo(2);
  }

  @Test
  void doesNotCacheUpstreamFailuresOrRawContent() {
    AtomicLong now = new AtomicLong();
    CountingSource source = new CountingSource("<p>terms</p>", "<p>privacy</p>");
    source.failTerms = true;
    LegalContentService service = service(source, now);

    assertThatThrownBy(() -> service.document(LegalContentType.TERMS)).isInstanceOf(UpstreamException.class);
    assertThatThrownBy(() -> service.document(LegalContentType.TERMS)).isInstanceOf(UpstreamException.class);

    assertThat(source.termsCalls.get()).isEqualTo(2);
    assertThat(service.document(LegalContentType.PRIVACY).toString()).doesNotContain("raw-secret", "script");
  }

  @Test
  void concurrentRequestsShareOneSuccessfulLoad() throws Exception {
    AtomicLong now = new AtomicLong();
    CountingSource source = new CountingSource("<p>terms</p>", "<p>privacy</p>");
    source.termsStarted = new CountDownLatch(1);
    source.releaseTerms = new CountDownLatch(1);
    LegalContentService service = service(source, now);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<LegalContentDocument> first = executor.submit(() -> service.document(LegalContentType.TERMS));
      source.termsStarted.await();
      Future<LegalContentDocument> second = executor.submit(() -> service.document(LegalContentType.TERMS));
      source.releaseTerms.countDown();

      assertThat(first.get().contentHtml()).isEqualTo("<p>terms</p>");
      assertThat(second.get().contentHtml()).isEqualTo("<p>terms</p>");
    }
    assertThat(source.termsCalls.get()).isOne();
  }

  private static LegalContentService service(CountingSource source, AtomicLong now) {
    LegalContentProperties properties = new LegalContentProperties();
    properties.setSourceFormat(LegalContentFormat.HTML);
    properties.setCacheTtl(Duration.ofSeconds(10));
    return new LegalContentService(source, new LegalContentRenderer(properties), properties, now::get);
  }

  private static final class CountingSource implements NewApiLegalContentSource {
    private final AtomicInteger termsCalls = new AtomicInteger();
    private final AtomicInteger privacyCalls = new AtomicInteger();
    private String terms;
    private final String privacy;
    private boolean failTerms;
    private CountDownLatch termsStarted;
    private CountDownLatch releaseTerms;

    private CountingSource(String terms, String privacy) {
      this.terms = terms;
      this.privacy = privacy;
    }

    @Override
    public String getUserAgreement() {
      termsCalls.incrementAndGet();
      if (termsStarted != null) {
        termsStarted.countDown();
      }
      await(releaseTerms);
      if (failTerms) {
        throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
      }
      return terms;
    }

    @Override
    public String getPrivacyPolicy() {
      privacyCalls.incrementAndGet();
      return privacy;
    }

    private static void await(CountDownLatch latch) {
      if (latch == null) {
        return;
      }
      try {
        latch.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
  }
}
