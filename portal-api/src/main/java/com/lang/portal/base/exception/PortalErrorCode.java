package com.lang.portal.base.exception;

import org.springframework.http.HttpStatus;

public enum PortalErrorCode {
  INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, "请求参数不合法"),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "尚未登录或登录已过期"),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "没有访问权限"),
  REGISTRATION_DISABLED(HttpStatus.FORBIDDEN, "当前环境未开放注册"),
  CSRF_REJECTED(HttpStatus.FORBIDDEN, "请求安全校验失败"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "请求的资源不存在"),
  RESOURCE_CONFLICT(HttpStatus.CONFLICT, "资源已存在或状态冲突"),
  API_KEY_LIMIT_REACHED(HttpStatus.CONFLICT, "API Key 数量已达上限"),
  OPERATION_RESULT_UNKNOWN(HttpStatus.BAD_GATEWAY, "操作结果未知，请刷新核对"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "不支持的请求方法"),
  PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "请求正文过大"),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后重试"),
  UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "上游服务异常，请稍后重试"),
  UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "上游服务不可用，请稍后重试"),
  UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "上游服务超时，请稍后重试"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试");

  private final HttpStatus status;
  private final String message;

  PortalErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public HttpStatus status() {
    return status;
  }

  public String message() {
    return message;
  }
}
