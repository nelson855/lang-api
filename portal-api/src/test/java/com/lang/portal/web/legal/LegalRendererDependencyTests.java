package com.lang.portal.web.legal;

import static org.assertj.core.api.Assertions.assertThat;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.junit.jupiter.api.Test;
import org.owasp.html.HtmlPolicyBuilder;

class LegalRendererDependencyTests {

  @Test
  void markdownRendererAndHtmlSanitizerAreAvailableForTheLegalContentBoundary() {
    String rendered = HtmlRenderer.builder().build().render(Parser.builder().build().parse("# Legal"));
    String sanitized = new HtmlPolicyBuilder().allowElements("h1").toFactory()
        .sanitize(rendered + "<script>unsafe()</script>");

    assertThat(sanitized).contains("<h1>Legal</h1>").doesNotContain("script", "unsafe");
  }
}
