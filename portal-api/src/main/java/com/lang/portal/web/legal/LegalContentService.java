package com.lang.portal.web.legal;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.LegalContentProperties;
import com.lang.portal.upstream.newapi.legal.NewApiLegalContentSource;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LegalContentService {

  private final NewApiLegalContentSource source;
  private final LegalContentRenderer renderer;
  private final LegalContentProperties properties;
  private final LongSupplier nowMillis;
  private final Map<LegalContentType, CacheEntry> cache = new EnumMap<>(LegalContentType.class);

  @Autowired
  public LegalContentService(
      NewApiLegalContentSource source,
      LegalContentRenderer renderer,
      LegalContentProperties properties) {
    this(source, renderer, properties, System::currentTimeMillis);
  }

  LegalContentService(
      NewApiLegalContentSource source,
      LegalContentRenderer renderer,
      LegalContentProperties properties,
      LongSupplier nowMillis) {
    this.source = source;
    this.renderer = renderer;
    this.properties = properties;
    this.nowMillis = nowMillis;
  }

  public LegalContentDocument document(LegalContentType type) {
    synchronized (cache) {
      long now = nowMillis.getAsLong();
      CacheEntry existing = cache.get(type);
      if (existing != null && existing.expiresAtMillis() > now) {
        return existing.document();
      }
      String raw = type == LegalContentType.TERMS ? source.getUserAgreement() : source.getPrivacyPolicy();
      String contentHtml = renderer.render(raw)
          .orElseThrow(() -> new PortalException(PortalErrorCode.NOT_FOUND));
      LegalContentDocument document = new LegalContentDocument(
          type,
          type == LegalContentType.TERMS ? "用户协议" : "隐私政策",
          contentHtml,
          properties.sourceLocale());
      cache.put(type, new CacheEntry(document, now + properties.cacheTtl().toMillis()));
      return document;
    }
  }

  private record CacheEntry(LegalContentDocument document, long expiresAtMillis) {}
}
