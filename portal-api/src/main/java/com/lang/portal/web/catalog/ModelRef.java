package com.lang.portal.web.catalog;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ModelRef {
  private ModelRef() {}

  public static String encode(String id) {
    if (!CatalogPriceMapper.isValidModelId(id)) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT);
    }
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(id.getBytes(StandardCharsets.UTF_8));
  }

  public static String decode(String ref) {
    if (ref == null || ref.isEmpty() || ref.length() > 512 || !isUrlSafeAlphabet(ref)) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT);
    }
    byte[] bytes;
    try {
      bytes = Base64.getUrlDecoder().decode(ref);
    } catch (IllegalArgumentException e) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT);
    }
    String id = strictUtf8(bytes);
    if (!CatalogPriceMapper.isValidModelId(id)) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT);
    }
    String canonical =
        Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8));
    if (!canonical.equals(ref)) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT);
    }
    return id;
  }

  private static boolean isUrlSafeAlphabet(String ref) {
    for (int i = 0; i < ref.length(); i++) {
      char c = ref.charAt(i);
      boolean ok =
          (c >= 'A' && c <= 'Z')
              || (c >= 'a' && c <= 'z')
              || (c >= '0' && c <= '9')
              || c == '-'
              || c == '_';
      if (!ok) {
        return false;
      }
    }
    return true;
  }

  private static String strictUtf8(byte[] bytes) {
    CharsetDecoder decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      CharBuffer out = decoder.decode(ByteBuffer.wrap(bytes));
      return out.toString();
    } catch (CharacterCodingException e) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT);
    }
  }
}
