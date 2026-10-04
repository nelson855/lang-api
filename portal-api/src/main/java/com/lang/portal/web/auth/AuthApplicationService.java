package com.lang.portal.web.auth;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.infrastructure.session.PortalSessionRecord;
import com.lang.portal.infrastructure.session.PortalSessionStore;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiCredentials;
import com.lang.portal.upstream.newapi.auth.NewApiLoginResult;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import org.springframework.stereotype.Service;

@Service
public class AuthApplicationService {

  private final RegistrationPolicyService registrationPolicy;
  private final NewApiAuthenticationClient authenticationClient;
  private final PortalSessionStore sessionStore;

  public AuthApplicationService(
      RegistrationPolicyService registrationPolicy,
      NewApiAuthenticationClient authenticationClient,
      PortalSessionStore sessionStore) {
    this.registrationPolicy = registrationPolicy;
    this.authenticationClient = authenticationClient;
    this.sessionStore = sessionStore;
  }

  public void register(RegisterRequest request) {
    if (!registrationPolicy.evaluate().registrationEnabled()) {
      throw new PortalException(PortalErrorCode.REGISTRATION_DISABLED);
    }
    if (!request.password().equals(request.confirmPassword())) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT);
    }
    authenticationClient.register(new NewApiCredentials(request.username(), request.password()));
  }

  public AuthLoginResult login(LoginRequest request) {
    NewApiLoginResult result = authenticationClient.login(new NewApiCredentials(request.username(), request.password()));
    var user = result.user();
    // 每次登录签发独立的服务端会话；浏览器只拿到不透明标识，上游会话值不出服务端。
    String sessionId = sessionStore.create(result.session().value(), result.session().userId());
    return new AuthLoginResult(
        new PortalSessionHandle(sessionId, result.session().userId()),
        new AuthProfile(user.id(), user.username(), user.displayName(), user.email()));
  }

  /**
   * 撤销一次 Portal 会话：先按服务端记录删除本地会话，再尝试退出上游。
   *
   * <p>上游退出失败时也必须完成本地撤销，否则旧 Cookie 仍可用。
   *
   * @return 本次实际撤销的上游会话；没有服务端记录时返回 {@code null}
   */
  public NewApiSession revoke(String sessionId, long userId) {
    PortalSessionRecord record = sessionStore.find(sessionId);
    if (record == null) {
      return null;
    }
    NewApiSession session = new NewApiSession(record.upstreamSessionValue(), record.userId());
    boolean revoked = sessionStore.delete(sessionId);
    if (!revoked) {
      // 记录在查找与删除之间已被其他实例撤销：本地已失效，不再调用上游。
      return null;
    }
    authenticationClient.logout(session);
    return session;
  }
}
