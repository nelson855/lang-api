package com.lang.portal.web.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import com.lang.portal.upstream.newapi.balance.NewApiBalanceClient;
import com.lang.portal.upstream.newapi.topup.NewApiTopupInfoClient;
import com.lang.portal.upstream.newapi.topup.NewApiTopupRecordsClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountSecurityRoutingTests {

  @Autowired private MockMvc mvc;

  @MockBean private NewApiAuthenticationClient authenticationClient;
  @MockBean private NewApiBalanceClient balanceClient;
  @MockBean private NewApiTopupInfoClient topupInfoClient;
  @MockBean private NewApiTopupRecordsClient topupRecordsClient;

  @Test
  void anonymousTopupsIsUnauthenticatedWithoutUpstream() throws Exception {
    mvc.perform(get("/portal/api/account/topups"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    verifyNoInteractions(authenticationClient, balanceClient, topupInfoClient, topupRecordsClient);
  }

  @Test
  void anonymousTopupOptionsIsUnauthenticatedWithoutUpstream() throws Exception {
    mvc.perform(get("/portal/api/account/topup-options"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    verifyNoInteractions(authenticationClient, balanceClient, topupInfoClient, topupRecordsClient);
  }

  @Test
  void topupOrdersPostIsJsonNotFoundWithoutUpstream() throws Exception {
    mvc.perform(
            post("/portal/api/account/topup-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"10\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    verifyNoInteractions(authenticationClient, balanceClient, topupInfoClient, topupRecordsClient);
  }

  @Test
  void paymentCallbackPathsAreJsonNotFoundWithoutUpstream() throws Exception {
    mvc.perform(post("/portal/api/account/topup-callback").content("{}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    mvc.perform(get("/portal/api/pay/callback"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    verifyNoInteractions(authenticationClient, balanceClient, topupInfoClient, topupRecordsClient);
  }

  @Test
  void balanceReusesSingleQuotedAmountWithNoStore() throws Exception {
    when(authenticationClient.currentUser(any()))
        .thenReturn(new NewApiUserProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test"));
    when(balanceClient.currentQuota(any())).thenReturn(1_000_000L);

    mvc.perform(
            get("/portal/api/account/balance")
                .cookie(new Cookie("LANG_SESSION", "upstream-session"), new Cookie("LANG_UID", "42")))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.currency").value("USD"))
        .andExpect(jsonPath("$.data.quota").value("1000000"))
        .andExpect(jsonPath("$.data.amount").value("2.0"));
  }
}
