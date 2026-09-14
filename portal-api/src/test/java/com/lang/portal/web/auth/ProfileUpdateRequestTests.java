package com.lang.portal.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileUpdateRequestTests {

  private static Map<String, String> validBase() {
    Map<String, String> params = new HashMap<>();
    params.put("username", "newname");
    params.put("displayName", "New Name");
    params.put("currentPassword", "correct-horse-123");
    return params;
  }

  @Test
  void acceptsFullTargetStateWithoutPasswordChange() {
    ProfileUpdateRequest request = ProfileUpdateRequest.resolve(validBase());

    assertThat(request.username()).isEqualTo("newname");
    assertThat(request.displayName()).isEqualTo("New Name");
    assertThat(request.newPassword()).isNull();
  }

  @Test
  void acceptsPairedNewPassword() {
    Map<String, String> params = validBase();
    params.put("newPassword", "brand-new-123");
    params.put("confirmPassword", "brand-new-123");

    ProfileUpdateRequest request = ProfileUpdateRequest.resolve(params);

    assertThat(request.newPassword()).isEqualTo("brand-new-123");
  }

  @Test
  void trimsUsernameAndDisplayName() {
    Map<String, String> params = validBase();
    params.put("username", "  spaced  ");
    params.put("displayName", "  Spaced Name  ");

    ProfileUpdateRequest request = ProfileUpdateRequest.resolve(params);

    assertThat(request.username()).isEqualTo("spaced");
    assertThat(request.displayName()).isEqualTo("Spaced Name");
  }

  @Test
  void rejectsMissingCurrentPassword() {
    Map<String, String> params = validBase();
    params.remove("currentPassword");

    assertThatThrownBy(() -> ProfileUpdateRequest.resolve(params))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void rejectsUnpairedOrMismatchedNewPassword() {
    Map<String, String> onlyNew = validBase();
    onlyNew.put("newPassword", "brand-new-123");
    assertThatThrownBy(() -> ProfileUpdateRequest.resolve(onlyNew))
        .isInstanceOf(PortalException.class);

    Map<String, String> mismatch = validBase();
    mismatch.put("newPassword", "brand-new-123");
    mismatch.put("confirmPassword", "different-123");
    assertThatThrownBy(() -> ProfileUpdateRequest.resolve(mismatch))
        .isInstanceOf(PortalException.class);
  }

  @Test
  void rejectsShortUsernameAndBlankDisplay() {
    Map<String, String> shortName = validBase();
    shortName.put("username", "ab");
    assertThatThrownBy(() -> ProfileUpdateRequest.resolve(shortName))
        .isInstanceOf(PortalException.class);
  }

  @Test
  void rejectsUnknownAndPrivilegedFields() {
    for (String field : new String[] {"email", "phone", "role", "group", "quota", "unknownField"}) {
      Map<String, String> params = validBase();
      params.put(field, "x");
      assertThatThrownBy(() -> ProfileUpdateRequest.resolve(params))
          .isInstanceOf(PortalException.class)
          .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    }
  }
}
