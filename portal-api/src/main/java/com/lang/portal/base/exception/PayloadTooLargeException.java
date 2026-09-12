package com.lang.portal.base.exception;

public class PayloadTooLargeException extends PortalException {
  public PayloadTooLargeException() {
    super(PortalErrorCode.PAYLOAD_TOO_LARGE);
  }
}
