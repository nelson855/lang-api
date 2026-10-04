package com.lang.portal.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ModelRefTests {

  @Test
  void roundTripSimpleId() {
    String ref = ModelRef.encode("gpt-4o-mini");
    assertThat(ref).matches("[A-Za-z0-9_-]+");
    assertThat(ModelRef.decode(ref)).isEqualTo("gpt-4o-mini");
  }

  @Test
  void roundTripSpecialChars() {
    String id = "vendor/model%name with 空格+unicode-🎉";
    String ref = ModelRef.encode(id);
    assertThat(ref).doesNotContain("/", "+", "=", " ");
    assertThat(ModelRef.decode(ref)).isEqualTo(id);
  }

  @Test
  void encodeMatchesUrlSafeBase64WithoutPadding() {
    String id = "a/b%c d";
    String expected =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(id.getBytes(StandardCharsets.UTF_8));
    assertThat(ModelRef.encode(id)).isEqualTo(expected);
  }

  @Test
  void decodeDoesNotTrimOrFold() {
    String id = "  AbC  ";
    String ref = ModelRef.encode(id);
    assertThat(ModelRef.decode(ref)).isEqualTo("  AbC  ");
  }

  @Test
  void rejectEmptyAndNull() {
    assertThatThrownBy(() -> ModelRef.decode(null))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(() -> ModelRef.decode(""))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(() -> ModelRef.encode(null))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(() -> ModelRef.encode(""))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void rejectPaddingAndIllegalAlphabet() {
    assertThatThrownBy(() -> ModelRef.decode("YWJj="))
        .isInstanceOf(PortalException.class);
    assertThatThrownBy(() -> ModelRef.decode("ab+cd"))
        .isInstanceOf(PortalException.class);
    assertThatThrownBy(() -> ModelRef.decode("ab/cd"))
        .isInstanceOf(PortalException.class);
    assertThatThrownBy(() -> ModelRef.decode("ab cd"))
        .isInstanceOf(PortalException.class);
    assertThatThrownBy(() -> ModelRef.decode("ab*cd"))
        .isInstanceOf(PortalException.class);
  }

  @Test
  void rejectNonCanonicalEncoding() {
    // "abc" 的规范编码是 YWJj，"YWKj" 解码后重编码不一致，属于非规范 trailing bits
    String canonical = ModelRef.encode("abc");
    assertThat(canonical).isEqualTo("YWJj");
    assertThatThrownBy(() -> ModelRef.decode("YWKj"))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void rejectIllegalUtf8() {
    byte[] illegal = new byte[] {(byte) 0xFF, (byte) 0xFE};
    String ref =
        Base64.getUrlEncoder().withoutPadding().encodeToString(illegal);
    assertThatThrownBy(() -> ModelRef.decode(ref))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void rejectDecodedIdViolatingCatalogBoundaries() {
    // 控制字符
    String control =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("badid".getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> ModelRef.decode(control))
        .isInstanceOf(PortalException.class);
    // 超长 129
    String longId = "x".repeat(129);
    String longRef =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(longId.getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> ModelRef.decode(longRef))
        .isInstanceOf(PortalException.class);
    // encode 侧同样拒绝非法 ID
    assertThatThrownBy(() -> ModelRef.encode("badid"))
        .isInstanceOf(PortalException.class);
    assertThatThrownBy(() -> ModelRef.encode(longId))
        .isInstanceOf(PortalException.class);
  }
}
