package com.lang.portal.web.apikey;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import com.lang.portal.upstream.newapi.token.NewApiCreateTokenCommand;
import com.lang.portal.upstream.newapi.token.NewApiToken;
import com.lang.portal.upstream.newapi.token.NewApiTokenClient;
import com.lang.portal.upstream.newapi.token.NewApiTokenPage;
import com.lang.portal.upstream.newapi.token.NewApiUpdateTokenCommand;
import com.lang.portal.upstream.newapi.token.SensitiveSecret;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeyJourneyIntegrationTests {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private NewApiAuthenticationClient authClient;
  @MockitoBean private NewApiTokenClient tokenClient;

  private static final NewApiSession SESSION_A = new NewApiSession("session-a", 1L);
  private static final NewApiSession SESSION_B = new NewApiSession("session-b", 2L);

  private final Map<Long, Long> owners = new LinkedHashMap<>();
  private final Map<Long, NewApiToken> store = new LinkedHashMap<>();
  private final AtomicLong ids = new AtomicLong(100L);

  private NewApiToken token(long id, long userId, String name, int status) {
    return new NewApiToken(
        id, userId, "fN95**********CMHQ", status, name, 1789180807L, 1789180807L,
        -1L, 100L, false, false, "", "", 0L, "", false);
  }

  @BeforeEach
  void stubUpstream() {
    owners.clear();
    store.clear();
    when(authClient.currentUser(SESSION_A))
        .thenReturn(new NewApiUserProfile(1L, "user-a", "User A", "a@example.test"));
    when(authClient.currentUser(SESSION_B))
        .thenReturn(new NewApiUserProfile(2L, "user-b", "User B", "b@example.test"));

    org.mockito.Mockito.doAnswer((call) -> {
      NewApiSession session = call.getArgument(0);
      NewApiCreateTokenCommand command = call.getArgument(1);
      long id = ids.getAndIncrement();
      owners.put(id, session.userId());
      store.put(id, token(id, session.userId(), command.name(), 1));
      return null;
    }).when(tokenClient).createToken(any(), any());
    Answer<NewApiTokenPage> pages = (call) -> {
      NewApiSession session = call.getArgument(0);
      List<NewApiToken> items = new ArrayList<>();
      for (Map.Entry<Long, NewApiToken> entry : store.entrySet()) {
        if (owners.get(entry.getKey()) == session.userId()) {
          items.add(entry.getValue());
        }
      }
      items.sort((left, right) -> Long.compare(right.id(), left.id()));
      return new NewApiTokenPage(1, 20, items.size(), items);
    };
    when(tokenClient.listTokens(any(), anyInt(), anyInt())).thenAnswer(pages);
    when(tokenClient.searchTokens(any(), anyString(), anyInt(), anyInt())).thenAnswer(pages);
    when(tokenClient.getToken(any(), anyLong())).thenAnswer((call) -> {
      NewApiSession session = call.getArgument(0);
      long id = call.getArgument(1);
      if (!owners.containsKey(id) || owners.get(id) != session.userId()) {
        throw new PortalException(PortalErrorCode.NOT_FOUND);
      }
      return store.get(id);
    });
    when(tokenClient.revealToken(any(), anyLong())).thenAnswer((call) -> {
      NewApiSession session = call.getArgument(0);
      long id = call.getArgument(1);
      if (!owners.containsKey(id) || owners.get(id) != session.userId()) {
        throw new PortalException(PortalErrorCode.NOT_FOUND);
      }
      return SensitiveSecret.of("sk-test-secret");
    });
  }

  private Csrf csrf() throws Exception {
    MvcResult result = mvc.perform(get("/portal/api/auth/csrf"))
        .andExpect(status().isOk())
        .andReturn();
    String token = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
    return new Csrf(token);
  }

  private Cookie[] cookies(NewApiSession session) {
    return new Cookie[] {
      new Cookie("LANG_SESSION", session.value()), new Cookie("LANG_UID", Long.toString(session.userId())),
    };
  }

  @Test
  void fullKeyLifecycleOnlyTouchesCurrentUser() throws Exception {
    Csrf csrf = csrf();

    mvc.perform(post("/portal/api/api-keys").cookie(cookies(SESSION_A))
            .contentType(MediaType.APPLICATION_JSON)
            .header("Origin", "http://portal.test").header("X-XSRF-TOKEN", csrf.token())
            .cookie(new Cookie("XSRF-TOKEN", csrf.token()))
            .content("{\"name\":\"journey-key\",\"unlimited\":false,\"remaining\":100}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.created").value(true))
        .andExpect(jsonPath("$.data.id").doesNotExist())
        .andExpect(jsonPath("$.data.secret").doesNotExist());

    mvc.perform(get("/portal/api/api-keys").cookie(cookies(SESSION_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].maskedKey").value("sk-fN95**********CMHQ"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());

    mvc.perform(get("/portal/api/api-keys").param("name", "journey").cookie(cookies(SESSION_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));

    mvc.perform(get("/portal/api/api-keys").param("status", "disabled").cookie(cookies(SESSION_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));

    mvc.perform(get("/portal/api/api-keys/100").cookie(cookies(SESSION_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("journey-key"));

    org.mockito.Mockito.doReturn(token(100L, 1L, "journey-key", 1))
        .when(tokenClient).getToken(eq(SESSION_A), eq(100L));
    mvc.perform(put("/portal/api/api-keys/100").cookie(cookies(SESSION_A))
            .contentType(MediaType.APPLICATION_JSON)
            .header("Origin", "http://portal.test").header("X-XSRF-TOKEN", csrf.token())
            .cookie(new Cookie("XSRF-TOKEN", csrf.token()))
            .content("{\"name\":\"renamed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updated").value(true));

    mvc.perform(put("/portal/api/api-keys/100/status").cookie(cookies(SESSION_A))
            .contentType(MediaType.APPLICATION_JSON)
            .header("Origin", "http://portal.test").header("X-XSRF-TOKEN", csrf.token())
            .cookie(new Cookie("XSRF-TOKEN", csrf.token()))
            .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enabled").value(false));

    mvc.perform(post("/portal/api/api-keys/100/reveal").cookie(cookies(SESSION_A))
            .header("Origin", "http://portal.test").header("X-XSRF-TOKEN", csrf.token())
            .cookie(new Cookie("XSRF-TOKEN", csrf.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.secret").value("sk-test-secret"))
        .andExpect(header().string("Cache-Control", "no-store"));

    mvc.perform(delete("/portal/api/api-keys/100").cookie(cookies(SESSION_A))
            .header("Origin", "http://portal.test").header("X-XSRF-TOKEN", csrf.token())
            .cookie(new Cookie("XSRF-TOKEN", csrf.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.deleted").value(true));
    owners.remove(100L);
    store.remove(100L);

    mvc.perform(get("/portal/api/api-keys").cookie(cookies(SESSION_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));

    mvc.perform(get("/portal/api/api-keys").cookie(cookies(SESSION_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
    mvc.perform(get("/portal/api/api-keys/100").cookie(cookies(SESSION_B)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    mvc.perform(post("/portal/api/api-keys/100/reveal").cookie(cookies(SESSION_B))
            .header("Origin", "http://portal.test").header("X-XSRF-TOKEN", csrf.token())
            .cookie(new Cookie("XSRF-TOKEN", csrf.token())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  private record Csrf(String token) {}
}
