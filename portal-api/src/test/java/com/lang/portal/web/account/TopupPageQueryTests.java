package com.lang.portal.web.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TopupPageQueryTests {

  @Test
  void defaultsToPageOneAndTwentyWhenOmitted() {
    TopupPageQuery query = TopupPageQuery.resolve(Map.of(), 20, 100);

    assertThat(query.page()).isEqualTo(1);
    assertThat(query.pageSize()).isEqualTo(20);
  }

  @Test
  void acceptsLegalPageAndPageSize() {
    TopupPageQuery query = TopupPageQuery.resolve(Map.of("page", "2", "pageSize", "50"), 20, 100);

    assertThat(query.page()).isEqualTo(2);
    assertThat(query.pageSize()).isEqualTo(50);
  }

  @Test
  void rejectsNonPositivePage() {
    assertThatThrownBy(() -> TopupPageQuery.resolve(Map.of("page", "0"), 20, 100))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(() -> TopupPageQuery.resolve(Map.of("page", "-1"), 20, 100))
        .isInstanceOf(PortalException.class);
    assertThatThrownBy(() -> TopupPageQuery.resolve(Map.of("page", "abc"), 20, 100))
        .isInstanceOf(PortalException.class);
  }

  @Test
  void rejectsPageSizeBeyondMax() {
    assertThatThrownBy(() -> TopupPageQuery.resolve(Map.of("pageSize", "101"), 20, 100))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(() -> TopupPageQuery.resolve(Map.of("pageSize", "0"), 20, 100))
        .isInstanceOf(PortalException.class);
  }

  @Test
  void rejectsUnknownOrPrivilegedParams() {
    assertThatThrownBy(() -> TopupPageQuery.resolve(Map.of("userId", "1"), 20, 100))
        .isInstanceOf(PortalException.class);
    assertThatThrownBy(() -> TopupPageQuery.resolve(Map.of("status", "success"), 20, 100))
        .isInstanceOf(PortalException.class);
    assertThatThrownBy(() -> TopupPageQuery.resolve(Map.of("username", "a"), 20, 100))
        .isInstanceOf(PortalException.class);
  }
}
