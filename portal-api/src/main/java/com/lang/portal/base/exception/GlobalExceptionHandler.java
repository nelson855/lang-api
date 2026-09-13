package com.lang.portal.base.exception;

import com.lang.portal.base.log.SensitiveDataRedactor;
import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.response.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final com.lang.portal.infrastructure.web.PortalApiNotFoundHandler notFoundHandler;

  public GlobalExceptionHandler(com.lang.portal.infrastructure.web.PortalApiNotFoundHandler notFoundHandler) {
    this.notFoundHandler = notFoundHandler;
  }

  @ExceptionHandler(PortalException.class)
  public ResponseEntity<ApiResponse<Void>> handlePortal(PortalException e, HttpServletRequest request) {
    return body(e.errorCode(), request);
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
  public ResponseEntity<ApiResponse<Void>> handleValidation(Exception e, HttpServletRequest request) {
    String field = firstField(e);
    String message =
        field == null ? PortalErrorCode.INVALID_ARGUMENT.message() : "请求参数 " + field + " 不合法";
    return bodyWithMessage(PortalErrorCode.INVALID_ARGUMENT, message, request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException e, HttpServletRequest request) {
    String field = e.getConstraintViolations().stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .findFirst()
        .map(p -> p.contains(".") ? p.substring(p.lastIndexOf('.') + 1) : p)
        .orElse(null);
    String message =
        field == null ? PortalErrorCode.INVALID_ARGUMENT.message() : "请求参数 " + field + " 不合法";
    return bodyWithMessage(PortalErrorCode.INVALID_ARGUMENT, message, request);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException e, HttpServletRequest request) {
    return body(PortalErrorCode.INVALID_ARGUMENT, request);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
    return bodyWithMessage(PortalErrorCode.INVALID_ARGUMENT, "请求参数 " + e.getParameterName() + " 不合法", request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
    log.warn("event=request_unreadable requestId={}", RequestIds.current(request));
    return body(PortalErrorCode.INVALID_ARGUMENT, request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
    return body(PortalErrorCode.METHOD_NOT_ALLOWED, request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e, HttpServletRequest request)
      throws NoResourceFoundException {
    String path = e.getResourcePath();
    if (path != null && path.startsWith("portal/api/")) {
      return notFoundHandler.unifiedNotFound(request);
    }
    throw e;
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException e, HttpServletRequest request) {
    return body(PortalErrorCode.UNAUTHENTICATED, request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException e, HttpServletRequest request) {
    return isAnonymous() ? body(PortalErrorCode.UNAUTHENTICATED, request) : body(PortalErrorCode.FORBIDDEN, request);
  }

  @ExceptionHandler(AuthorizationDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAuthorizationDenied(AuthorizationDeniedException e, HttpServletRequest request) {
    return isAnonymous() ? body(PortalErrorCode.UNAUTHENTICATED, request) : body(PortalErrorCode.FORBIDDEN, request);
  }

  private boolean isAnonymous() {
    var authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return true;
    }
    if (authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
      return true;
    }
    return authentication.getAuthorities().stream()
        .anyMatch(a -> "ROLE_ANONYMOUS".equals(a.getAuthority()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e, HttpServletRequest request) {
    String redacted = SensitiveDataRedactor.redact(String.valueOf(e.getMessage()));
    log.warn("event=internal_error requestId={} kind={} detail={}",
        RequestIds.current(request), e.getClass().getSimpleName(), redacted);
    return body(PortalErrorCode.INTERNAL_ERROR, request);
  }

  private ResponseEntity<ApiResponse<Void>> body(PortalErrorCode code, HttpServletRequest request) {
    return bodyWithMessage(code, code.message(), request);
  }

  private ResponseEntity<ApiResponse<Void>> bodyWithMessage(
      PortalErrorCode code, String message, HttpServletRequest request) {
    String requestId = RequestIds.current(request);
    if (requestId.isBlank()) {
      requestId = RequestIds.generate();
    }
    ResponseEntity.BodyBuilder response = ResponseEntity.status(code.status());
    if (code == PortalErrorCode.RATE_LIMITED) {
      response.header("Retry-After", "60");
    }
    Object pendingCookies = request.getAttribute(
        com.lang.portal.web.auth.AuthenticationController.EXPIRE_SESSION_COOKIES_ATTRIBUTE);
    if (pendingCookies instanceof List<?> cookies) {
      cookies.stream()
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .forEach(cookie -> response.header("Set-Cookie", cookie));
    }
    return response.body(ApiResponses.failure(requestId, code.name(), message));
  }

  private String firstField(Exception e) {
    if (e instanceof MethodArgumentNotValidException manv && manv.getFieldError() != null) {
      return manv.getFieldError().getField();
    }
    if (e instanceof BindException be && be.getFieldError() != null) {
      return be.getFieldError().getField();
    }
    return null;
  }
}
