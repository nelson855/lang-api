package com.lang.portal.web.legal;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegalContentControllerTests {

  @Autowired private MockMvc mvc;

  @MockBean private LegalContentService service;

  @Test
  void returnsOnlyThePublishedTermsDocumentForAnonymousGet() throws Exception {
    given(service.document(LegalContentType.TERMS)).willReturn(
        new LegalContentDocument(LegalContentType.TERMS, "用户协议", "<p>safe</p>", "zh-CN"));

    mvc.perform(get("/portal/api/legal/terms").header("Cookie", "session=untrusted"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.type").value("TERMS"))
        .andExpect(jsonPath("$.data.title").value("用户协议"))
        .andExpect(jsonPath("$.data.contentHtml").value("<p>safe</p>"))
        .andExpect(jsonPath("$.data.locale").value("zh-CN"))
        .andExpect(jsonPath("$.data.raw").doesNotExist());
  }

  @Test
  void returnsOnlyThePublishedPrivacyDocumentForAnonymousGet() throws Exception {
    given(service.document(LegalContentType.PRIVACY)).willReturn(
        new LegalContentDocument(LegalContentType.PRIVACY, "隐私政策", "<p>safe</p>", "zh-CN"));

    mvc.perform(get("/portal/api/legal/privacy").header("Authorization", "Bearer untrusted"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.type").value("PRIVACY"))
        .andExpect(jsonPath("$.data.title").value("隐私政策"))
        .andExpect(jsonPath("$.data.contentHtml").value("<p>safe</p>"))
        .andExpect(jsonPath("$.data.locale").value("zh-CN"))
        .andExpect(jsonPath("$.data.raw").doesNotExist())
        .andExpect(jsonPath("$.data.message").doesNotExist());
  }

  @Test
  void mapsUnavailableDocumentToNotFoundWithoutUpstreamDetails() throws Exception {
    given(service.document(LegalContentType.PRIVACY)).willThrow(new PortalException(PortalErrorCode.NOT_FOUND));

    mvc.perform(get("/portal/api/legal/privacy"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("请求的资源不存在"));
  }

  @Test
  void preservesSafeUpstreamFailureMappingAndRejectsWrongMethods() throws Exception {
    given(service.document(LegalContentType.TERMS)).willThrow(new PortalException(PortalErrorCode.UPSTREAM_ERROR));

    mvc.perform(get("/portal/api/legal/terms"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error.code").value("UPSTREAM_ERROR"));
    mvc.perform(post("/portal/api/legal/terms"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    mvc.perform(post("/portal/api/legal/privacy"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void errorResponsesDoNotLeakRawContent() throws Exception {
    given(service.document(LegalContentType.TERMS)).willThrow(new PortalException(PortalErrorCode.NOT_FOUND));

    String body = mvc.perform(get("/portal/api/legal/terms"))
        .andExpect(status().isNotFound())
        .andReturn().getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(body).doesNotContain("raw-secret", "script");
  }
}
