package com.lang.portal.base.exception;

public class UpstreamException extends PortalException {
  public UpstreamException(PortalErrorCode errorCode) {
    super(errorCode);
    if (errorCode != PortalErrorCode.UPSTREAM_ERROR
        && errorCode != PortalErrorCode.UPSTREAM_UNAVAILABLE
        && errorCode != PortalErrorCode.UPSTREAM_TIMEOUT) {
      throw new IllegalArgumentException("仅允许上游错误码");
    }
  }
}
