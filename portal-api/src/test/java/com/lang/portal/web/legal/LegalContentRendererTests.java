package com.lang.portal.web.legal;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.config.LegalContentFormat;
import com.lang.portal.config.LegalContentProperties;
import org.junit.jupiter.api.Test;

class LegalContentRendererTests {

  @Test
  void rendersMarkdownThenRemovesEmbeddedActiveHtml() {
    LegalContentProperties properties = markdownProperties();

    String html = new LegalContentRenderer(properties)
        .render("# Terms\n\n- one\n- two\n\n<script>alert(1)</script><p onclick=\"alert(1)\">safe</p>")
        .orElseThrow();

    assertThat(html).contains("<h1>Terms</h1>", "<li>one</li>", "<p>safe</p>")
        .doesNotContain("script", "onclick", "alert(1)");
  }

  @Test
  void removesDangerousHtmlNodesAttributesAndProtocols() {
    LegalContentProperties properties = htmlProperties();

    String html = new LegalContentRenderer(properties)
        .render("<iframe src=\"https://evil.example\"></iframe><form><input></form>"
            + "<img src=\"https://evil.example/pixel\"><a href=\"javascript:alert(1)\">bad</a>")
        .orElseThrow();

    assertThat(html).doesNotContain("iframe", "form", "input", "img", "javascript:", "alert(1)");
  }

  @Test
  void retainsSafeDocumentStructureAndSecuresNewWindowLinks() {
    LegalContentProperties properties = htmlProperties();

    String html = new LegalContentRenderer(properties)
        .render("<h2>Heading</h2><blockquote>Quoted</blockquote><hr><pre><code>x</code></pre>"
            + "<table><thead><tr><th>Key</th></tr></thead><tbody><tr><td>Value</td></tr></tbody></table>"
            + "<a href=\"https://example.com/policy\" target=\"_blank\">external</a>")
        .orElseThrow();

    assertThat(html).contains("<h2>Heading</h2>", "<blockquote>Quoted</blockquote>", "<table>",
        "href=\"https://example.com/policy\"", "target=\"_blank\"", "rel=\"noopener noreferrer\"");
  }

  @Test
  void rejectsEmptySanitizedOutputAndInputOverTheUtf8Limit() {
    LegalContentProperties properties = htmlProperties();
    properties.setMaxInputBytes(16 * 1024L);
    properties.setMaxOutputBytes(16 * 1024L);
    LegalContentRenderer renderer = new LegalContentRenderer(properties);

    assertThat(renderer.render("<script>unsafe()</script>")).isEmpty();
    assertThat(renderer.render("中".repeat(16 * 1024))).isEmpty();
  }

  private static LegalContentProperties markdownProperties() {
    LegalContentProperties properties = new LegalContentProperties();
    properties.setSourceFormat(LegalContentFormat.MARKDOWN);
    return properties;
  }

  private static LegalContentProperties htmlProperties() {
    LegalContentProperties properties = new LegalContentProperties();
    properties.setSourceFormat(LegalContentFormat.HTML);
    return properties;
  }
}
