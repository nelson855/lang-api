package com.lang.portal.web.legal;

public record LegalContentDocument(
    LegalContentType type,
    String title,
    String contentHtml,
    String locale) {}
