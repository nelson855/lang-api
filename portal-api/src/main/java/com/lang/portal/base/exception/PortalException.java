package com.lang.portal.base.exception;

public class PortalException extends RuntimeException {
  private final PortalErrorCode errorCode;

  public PortalException(PortalErrorCode errorCode) {
    super(errorCode.message());
    this.errorCode = errorCode;
  }

  public PortalException(PortalErrorCode errorCode, String safeMessage) {
    super(safeMessage);
    this.errorCode = errorCode;
  }

  public PortalErrorCode errorCode() {
    return errorCode;
  }
}
