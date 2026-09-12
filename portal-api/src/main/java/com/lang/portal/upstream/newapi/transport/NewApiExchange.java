package com.lang.portal.upstream.newapi.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.base.response.RequestIds;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.dto.NewApiEnvelope;
import com.lang.portal.upstream.newapi.operation.NewApiOperation;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.policy.NewApiHeaderPolicy;
import io.netty.handler.timeout.WriteTimeoutException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class NewApiExchange {

  private static final Logger log = LoggerFactory.getLogger(NewApiExchange.class);

  private final RestClient restClient;
  private final PortalCommonProperties properties;
  private final NewApiErrorTranslator translator;
  private final ObjectMapper mapper;

  public NewApiExchange(RestClient newApiRestClient, PortalCommonProperties properties, NewApiErrorTranslator translator) {
    this.restClient = newApiRestClient;
    this.properties = properties;
    this.translator = translator;
    this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public <T> T execute(NewApiOperation operation, Object requestBody, TypeReference<NewApiEnvelope<T>> type) {
    String baseUrl = properties.upstream().newApi().baseUrl();
    URI base = URI.create(baseUrl);
    URI target = base.resolve(operation.path());
    if (!target.getScheme().equalsIgnoreCase(base.getScheme())
        || !target.getHost().equalsIgnoreCase(base.getHost())
        || target.getPort() != base.getPort()) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    String requestId = currentRequestId();
    Map<String, String> headers = NewApiHeaderPolicy.requestHeaders(operation, requestId, Map.of());
    long start = System.currentTimeMillis();
    String outcome = "success";
    try {
      var spec = restClient.method(operation.method()).uri(target);
      headers.forEach(spec::header);
      if (requestBody != null) {
        spec.body(requestBody);
      }
      String raw = spec.retrieve().body(String.class);
      NewApiEnvelope<T> envelope;
      try {
        envelope = mapper.readValue(raw, type);
      } catch (Exception e) {
        outcome = "unparsable";
        throw translator.unparsable();
      }
      if (!envelope.success()) {
        outcome = "business_failure";
        throw translator.translate(200, false);
      }
      return envelope.data();
    } catch (UpstreamException e) {
      outcome = e.errorCode().name().toLowerCase();
      throw e;
    } catch (ResourceAccessException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof TimeoutException
          || cause instanceof WriteTimeoutException
          || cause instanceof io.netty.handler.timeout.ReadTimeoutException
          || cause instanceof java.net.SocketTimeoutException) {
        outcome = "timeout";
        throw new UpstreamException(PortalErrorCode.UPSTREAM_TIMEOUT);
      }
      outcome = "unavailable";
      throw new UpstreamException(PortalErrorCode.UPSTREAM_UNAVAILABLE);
    } catch (org.springframework.web.client.HttpStatusCodeException e) {
      outcome = "http_" + e.getStatusCode().value();
      throw translator.translate(e.getStatusCode().value(), null);
    } finally {
      log.info("event=new_api_call operation={} outcome={} durationMs={} requestId={}",
          operation.name(), outcome, System.currentTimeMillis() - start, requestId);
    }
  }

  private String currentRequestId() {
    try {
      var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs != null) {
        HttpServletRequest request = attrs.getRequest();
        String id = RequestIds.current(request);
        if (!id.isBlank()) {
          return id;
        }
      }
    } catch (Exception ignored) {
    }
    return RequestIds.generate();
  }
}
