package com.lang.portal.web.legal;

import com.lang.portal.config.LegalContentFormat;
import com.lang.portal.config.LegalContentProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.ElementPolicy;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

@Component
public class LegalContentRenderer {

  private final LegalContentProperties properties;
  private final Parser markdownParser = Parser.builder().build();
  private final HtmlRenderer markdownRenderer = HtmlRenderer.builder().escapeHtml(false).build();
  private final PolicyFactory policy = new HtmlPolicyBuilder()
      .allowElements("h1", "h2", "h3", "h4", "h5", "h6", "p", "ul", "ol", "li", "blockquote",
          "pre", "code", "table", "thead", "tbody", "tr", "th", "td", "hr", "strong", "em", "br")
      .allowUrlProtocols("https", "mailto")
      .allowAttributes("href", "target", "rel").onElements("a")
      .allowElements(new SecureAnchorPolicy(), "a")
      .toFactory();

  public LegalContentRenderer(LegalContentProperties properties) {
    this.properties = properties;
  }

  public Optional<String> render(String source) {
    if (source == null || exceeds(source, properties.maxInputBytes())) {
      return Optional.empty();
    }
    String html = properties.sourceFormat() == LegalContentFormat.MARKDOWN
        ? markdownRenderer.render(markdownParser.parse(source))
        : source;
    String sanitized = policy.sanitize(html).trim();
    if (sanitized.isBlank() || exceeds(sanitized, properties.maxOutputBytes())) {
      return Optional.empty();
    }
    return Optional.of(sanitized);
  }

  private static boolean exceeds(String value, long maximumBytes) {
    return value.getBytes(StandardCharsets.UTF_8).length > maximumBytes;
  }

  private static final class SecureAnchorPolicy implements ElementPolicy {
    @Override
    public String apply(String elementName, List<String> attributes) {
      int targetIndex = attributeIndex(attributes, "target");
      if (targetIndex >= 0 && "_blank".equalsIgnoreCase(attributes.get(targetIndex + 1))) {
        removeAttribute(attributes, "rel");
        attributes.add("rel");
        attributes.add("noopener noreferrer");
      }
      return elementName;
    }

    private static int attributeIndex(List<String> attributes, String name) {
      for (int index = 0; index < attributes.size(); index += 2) {
        if (name.equalsIgnoreCase(attributes.get(index))) {
          return index;
        }
      }
      return -1;
    }

    private static void removeAttribute(List<String> attributes, String name) {
      int index;
      while ((index = attributeIndex(attributes, name)) >= 0) {
        attributes.remove(index + 1);
        attributes.remove(index);
      }
    }
  }
}
