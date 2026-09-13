package com.lang.portal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "lang")
@Validated
public class PortalCommonProperties {

  @Valid @NotNull private Request request = new Request();
  @Valid @NotNull private Upstream upstream = new Upstream();
  @Valid @NotNull private Portal portal = new Portal();
  @Valid @NotNull private Auth auth = new Auth();
  private String env = "";

  public Request request() {
    return request;
  }

  public void setRequest(Request request) {
    this.request = request;
  }

  public Upstream upstream() {
    return upstream;
  }

  public void setUpstream(Upstream upstream) {
    this.upstream = upstream;
  }

  public Portal portal() {
    return portal;
  }

  public void setPortal(Portal portal) {
    this.portal = portal;
  }

  public Auth auth() {
    return auth;
  }

  public void setAuth(Auth auth) {
    this.auth = auth;
  }

  public String env() {
    return env;
  }

  public void setEnv(String env) {
    this.env = env;
  }

  @Validated
  public static class Request {
    @Min(1024)
    @Max(10 * 1024 * 1024)
    private long maxBodyBytes = 1_048_576;

    @Valid @NotNull private RequestId requestId = new RequestId();

    public long maxBodyBytes() {
      return maxBodyBytes;
    }

    public void setMaxBodyBytes(long maxBodyBytes) {
      this.maxBodyBytes = maxBodyBytes;
    }

    public RequestId requestId() {
      return requestId;
    }

    public void setRequestId(RequestId requestId) {
      this.requestId = requestId;
    }
  }

  @Validated
  public static class RequestId {
    @Min(1) @Max(128) private int minLength = 8;
    @Min(1) @Max(128) private int maxLength = 64;

    public int minLength() {
      return minLength;
    }

    public void setMinLength(int minLength) {
      this.minLength = minLength;
    }

    public int maxLength() {
      return maxLength;
    }

    public void setMaxLength(int maxLength) {
      this.maxLength = maxLength;
    }
  }

  @Validated
  public static class Upstream {
    @Valid @NotNull private NewApi newApi = new NewApi();

    public NewApi newApi() {
      return newApi;
    }

    public void setNewApi(NewApi newApi) {
      this.newApi = newApi;
    }
  }

  @Validated
  public static class NewApi {
    @NotBlank private String baseUrl = "http://localhost:1";

    @Valid @NotNull private Pool pool = new Pool();

    @NotNull private Duration connectTimeout = Duration.ofSeconds(2);
    @NotNull private Duration readTimeout = Duration.ofSeconds(5);
    @NotNull private Duration writeTimeout = Duration.ofSeconds(5);

    public String baseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public Pool pool() {
      return pool;
    }

    public void setPool(Pool pool) {
      this.pool = pool;
    }

    public Duration connectTimeout() {
      return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    public Duration readTimeout() {
      return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
      this.readTimeout = readTimeout;
    }

    public Duration writeTimeout() {
      return writeTimeout;
    }

    public void setWriteTimeout(Duration writeTimeout) {
      this.writeTimeout = writeTimeout;
    }
  }

  @Validated
  public static class Pool {
    @Min(1) @Max(500) private int maxConnections = 50;
    @Min(1) @Max(1000) private int maxPendingAcquire = 100;
    @NotNull private Duration acquireTimeout = Duration.ofSeconds(1);
    @NotNull private Duration maxIdleTime = Duration.ofSeconds(30);
    @NotNull private Duration maxLifeTime = Duration.ofMinutes(5);

    public int maxConnections() {
      return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
      this.maxConnections = maxConnections;
    }

    public int maxPendingAcquire() {
      return maxPendingAcquire;
    }

    public void setMaxPendingAcquire(int maxPendingAcquire) {
      this.maxPendingAcquire = maxPendingAcquire;
    }

    public Duration acquireTimeout() {
      return acquireTimeout;
    }

    public void setAcquireTimeout(Duration acquireTimeout) {
      this.acquireTimeout = acquireTimeout;
    }

    public Duration maxIdleTime() {
      return maxIdleTime;
    }

    public void setMaxIdleTime(Duration maxIdleTime) {
      this.maxIdleTime = maxIdleTime;
    }

    public Duration maxLifeTime() {
      return maxLifeTime;
    }

    public void setMaxLifeTime(Duration maxLifeTime) {
      this.maxLifeTime = maxLifeTime;
    }
  }

  @Validated
  public static class Portal {
    @NotBlank private String siteName = "Lang API";

    private List<String> enabledProtocols = new ArrayList<>();

    @Valid @NotNull private PublicUrls publicUrls = new PublicUrls();

    @Valid @NotNull private Cookie cookie = new Cookie();

    public String siteName() {
      return siteName;
    }

    public void setSiteName(String siteName) {
      this.siteName = siteName;
    }

    public List<String> enabledProtocols() {
      return enabledProtocols;
    }

    public List<String> publicProtocols() {
      return enabledProtocols;
    }

    public void setEnabledProtocols(List<String> enabledProtocols) {
      this.enabledProtocols = enabledProtocols == null ? new ArrayList<>() : enabledProtocols;
    }

    public void setPublicProtocols(List<String> publicProtocols) {
      setEnabledProtocols(publicProtocols);
    }

    public PublicUrls publicUrls() {
      return publicUrls;
    }

    public void setPublicUrls(PublicUrls publicUrls) {
      this.publicUrls = publicUrls;
    }

    public Cookie cookie() {
      return cookie;
    }

    public void setCookie(Cookie cookie) {
      this.cookie = cookie;
    }

    public Map<String, String> urlMap() {
      Map<String, String> map = new LinkedHashMap<>();
      if (publicUrls.openai() != null && !publicUrls.openai().isBlank()) {
        map.put("OPENAI", publicUrls.openai().trim());
      }
      if (publicUrls.anthropic() != null && !publicUrls.anthropic().isBlank()) {
        map.put("ANTHROPIC", publicUrls.anthropic().trim());
      }
      if (publicUrls.gemini() != null && !publicUrls.gemini().isBlank()) {
        map.put("GEMINI", publicUrls.gemini().trim());
      }
      return map;
    }
  }

  @Validated
  public static class PublicUrls {
    private String openai = "";
    private String anthropic = "";
    private String gemini = "";

    public String openai() {
      return openai;
    }

    public void setOpenai(String openai) {
      this.openai = openai;
    }

    public String anthropic() {
      return anthropic;
    }

    public void setAnthropic(String anthropic) {
      this.anthropic = anthropic;
    }

    public String gemini() {
      return gemini;
    }

    public void setGemini(String gemini) {
      this.gemini = gemini;
    }
  }

  @Validated
  public static class Cookie {
    private boolean secure = false;
    @NotBlank private String sameSite = "Lax";
    @NotBlank private String path = "/portal";

    public boolean secure() {
      return secure;
    }

    public void setSecure(boolean secure) {
      this.secure = secure;
    }

    public String sameSite() {
      return sameSite;
    }

    public void setSameSite(String sameSite) {
      this.sameSite = sameSite;
    }

    public String path() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }
  }

  @Validated
  public static class Auth {
    @Valid @NotNull private Registration registration = new Registration();
    @Valid @NotNull private AuthCookie cookie = new AuthCookie();
    @Valid @NotNull private Csrf csrf = new Csrf();
    @Valid @NotNull private RateLimit rateLimit = new RateLimit();
    @Valid @NotNull private TrustedProxy trustedProxy = new TrustedProxy();
    private boolean originCheckEnabled = true;
    private String allowedOrigins = "";
    private String trustedProxyCidrs = "";

    public Registration registration() {
      return registration;
    }

    public void setRegistration(Registration registration) {
      this.registration = registration;
    }

    public AuthCookie cookie() {
      return cookie;
    }

    public void setCookie(AuthCookie cookie) {
      this.cookie = cookie;
    }

    public Csrf csrf() {
      return csrf;
    }

    public void setCsrf(Csrf csrf) {
      this.csrf = csrf;
    }

    public RateLimit rateLimit() {
      return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
      this.rateLimit = rateLimit;
    }

    public TrustedProxy trustedProxy() {
      return trustedProxy;
    }

    public void setTrustedProxy(TrustedProxy trustedProxy) {
      this.trustedProxy = trustedProxy;
    }

    public boolean originCheckEnabled() {
      return originCheckEnabled;
    }

    public void setOriginCheckEnabled(boolean originCheckEnabled) {
      this.originCheckEnabled = originCheckEnabled;
    }

    public String allowedOrigins() {
      return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
      this.allowedOrigins = allowedOrigins;
    }

    public String trustedProxyCidrs() {
      return trustedProxyCidrs;
    }

    public void setTrustedProxyCidrs(String trustedProxyCidrs) {
      this.trustedProxyCidrs = trustedProxyCidrs;
    }

  }

  @Validated
  public static class Registration {
    private boolean enabled;

    public boolean enabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  @Validated
  public static class RateLimit {
    @Valid @NotNull private LoginRateLimit login = new LoginRateLimit();
    @Valid @NotNull private RegistrationRateLimit registration = new RegistrationRateLimit();
    @Min(1) @Max(100_000) private int maxEntries = 10_000;

    public LoginRateLimit login() {
      return login;
    }

    public void setLogin(LoginRateLimit login) {
      this.login = login;
    }

    public RegistrationRateLimit registration() {
      return registration;
    }

    public void setRegistration(RegistrationRateLimit registration) {
      this.registration = registration;
    }

    public int maxEntries() {
      return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
      this.maxEntries = maxEntries;
    }
  }

  @Validated
  public static class LoginRateLimit {
    private int usernameAttempts = 5;
    private int clientAttempts = 20;
    @NotNull private Duration window = Duration.ofMinutes(5);

    public int usernameAttempts() {
      return usernameAttempts;
    }

    public void setUsernameAttempts(int usernameAttempts) {
      this.usernameAttempts = usernameAttempts;
    }

    public int clientAttempts() {
      return clientAttempts;
    }

    public void setClientAttempts(int clientAttempts) {
      this.clientAttempts = clientAttempts;
    }

    public Duration window() {
      return window;
    }

    public void setWindow(Duration window) {
      this.window = window;
    }
  }

  @Validated
  public static class RegistrationRateLimit {
    private int clientAttempts = 3;
    @NotNull private Duration window = Duration.ofHours(1);

    public int clientAttempts() {
      return clientAttempts;
    }

    public void setClientAttempts(int clientAttempts) {
      this.clientAttempts = clientAttempts;
    }

    public Duration window() {
      return window;
    }

    public void setWindow(Duration window) {
      this.window = window;
    }
  }

  @Validated
  public static class TrustedProxy {
    private String cidrs = "";
    @NotBlank private String forwardedForHeader = "X-Forwarded-For";

    public String cidrs() {
      return cidrs;
    }

    public void setCidrs(String cidrs) {
      this.cidrs = cidrs;
    }

    public String forwardedForHeader() {
      return forwardedForHeader;
    }

    public void setForwardedForHeader(String forwardedForHeader) {
      this.forwardedForHeader = forwardedForHeader;
    }
  }

  @Validated
  public static class AuthCookie {
    @NotBlank private String sessionName = "LANG_SESSION";
    @NotBlank private String userIdName = "LANG_UID";
    @NotBlank private String path = "/portal";
    @NotBlank private String sameSite = "Lax";
    @NotNull private Duration maxAge = Duration.ofDays(30);
    private boolean secure;

    public String sessionName() {
      return sessionName;
    }

    public void setSessionName(String sessionName) {
      this.sessionName = sessionName;
    }

    public String userIdName() {
      return userIdName;
    }

    public void setUserIdName(String userIdName) {
      this.userIdName = userIdName;
    }

    public String path() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }

    public String sameSite() {
      return sameSite;
    }

    public void setSameSite(String sameSite) {
      this.sameSite = sameSite;
    }

    public Duration maxAge() {
      return maxAge;
    }

    public void setMaxAge(Duration maxAge) {
      this.maxAge = maxAge;
    }

    public boolean secure() {
      return secure;
    }

    public void setSecure(boolean secure) {
      this.secure = secure;
    }
  }

  @Validated
  public static class Csrf {
    @NotBlank private String cookieName = "XSRF-TOKEN";
    @NotBlank private String headerName = "X-XSRF-TOKEN";
    @NotBlank private String path = "/portal";
    @NotBlank private String sameSite = "Lax";

    public String cookieName() {
      return cookieName;
    }

    public void setCookieName(String cookieName) {
      this.cookieName = cookieName;
    }

    public String headerName() {
      return headerName;
    }

    public void setHeaderName(String headerName) {
      this.headerName = headerName;
    }

    public String path() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }

    public String sameSite() {
      return sameSite;
    }

    public void setSameSite(String sameSite) {
      this.sameSite = sameSite;
    }
  }
}
