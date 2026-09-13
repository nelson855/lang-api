package com.lang.portal.base.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiKeyErrorCodeContractTests {

  @Test
  void resourceConflictMapsTo409() {
    assertThat(PortalErrorCode.RESOURCE_CONFLICT.status()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(PortalErrorCode.RESOURCE_CONFLICT.message()).isNotBlank();
  }

  @Test
  void apiKeyLimitReachedMapsTo409() {
    assertThat(PortalErrorCode.API_KEY_LIMIT_REACHED.status()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(PortalErrorCode.API_KEY_LIMIT_REACHED.message()).isNotBlank();
  }

  @Test
  void operationResultUnknownMapsTo502() {
    assertThat(PortalErrorCode.OPERATION_RESULT_UNKNOWN.status())
        .isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(PortalErrorCode.OPERATION_RESULT_UNKNOWN.message()).isNotBlank();
  }

  @Test
  void apiKeyErrorMessagesStayGeneric() {
    assertThat(PortalErrorCode.RESOURCE_CONFLICT.message()).doesNotContain("sk-");
    assertThat(PortalErrorCode.API_KEY_LIMIT_REACHED.message()).doesNotContain("sk-");
    assertThat(PortalErrorCode.OPERATION_RESULT_UNKNOWN.message()).doesNotContain("sk-");
  }
}
