package com.lang.portal.upstream.newapi.policy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.UpstreamException;
import org.springframework.stereotype.Component;

@Component
public class NewApiErrorTranslator {

  public UpstreamException translate(int httpStatus, Boolean success) {
    if (Boolean.FALSE.equals(success)) {
      return new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    if (httpStatus == 408 || httpStatus == 504) {
      return new UpstreamException(PortalErrorCode.UPSTREAM_TIMEOUT);
    }
    if (httpStatus == 502 || httpStatus == 503) {
      return new UpstreamException(PortalErrorCode.UPSTREAM_UNAVAILABLE);
    }
    if (httpStatus < 200 || httpStatus >= 300) {
      return new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    return new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
  }

  public UpstreamException unparsable() {
    return new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
  }
}
