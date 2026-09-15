package com.lang.portal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class LegalPublicationPropertiesValidatorTests {

  @Test
  void defaultsToPreviewWithBoundedLegalProcessing() {
    PublicationProperties publication = new PublicationProperties();
    LegalContentProperties legal = new LegalContentProperties();

    assertThat(publication.mode()).isEqualTo(PublicationMode.PREVIEW);
    assertThat(legal.sourceLocale()).isEqualTo("zh-CN");
    assertThat(legal.sourceFormat()).isEqualTo(LegalContentFormat.MARKDOWN);
    assertThat(legal.maxInputBytes()).isBetween(16 * 1024L, 1024 * 1024L);
    assertThat(legal.maxOutputBytes()).isBetween(16 * 1024L, 1024 * 1024L);
    assertThat(legal.cacheTtl()).isBetween(Duration.ofSeconds(10), Duration.ofHours(1));
  }

  @Test
  void bindsCompletePublicPublicationConfiguration() {
    PublicSiteProperties site = bindSite(Map.of(
        "lang.public.site-url", "https://portal.example/",
        "lang.public.support-url", "mailto:support@portal.example",
        "lang.public.supported-regions[0]", "CN",
        "lang.public.enabled-locales[0]", "zh-CN"));

    assertThat(site.siteUrl()).isEqualTo("https://portal.example/");
    assertThat(site.supportUrl()).isEqualTo("mailto:support@portal.example");
    assertThat(site.supportedRegions()).containsExactly("CN");
    assertThat(site.enabledLocales()).containsExactly("zh-CN");
  }

  @Test
  void publicModeRequiresCompleteAndLocaleAlignedConfiguration() {
    PublicationProperties publication = new PublicationProperties();
    publication.setMode(PublicationMode.PUBLIC);
    PublicSiteProperties site = completeSite();
    LegalContentProperties legal = new LegalContentProperties();

    PortalPropertiesValidator.validatePublication(publication, site, legal);
  }

  @Test
  void publicModeRejectsNonHttpsSiteUrl() {
    PublicationProperties publication = publicMode();
    PublicSiteProperties site = completeSite();
    site.setSiteUrl("http://portal.example");

    assertThatThrownBy(() -> PortalPropertiesValidator.validatePublication(publication, site, new LegalContentProperties()))
        .hasMessageContaining("lang.public.site-url");
  }

  @Test
  void rejectsPublicUrlWithCredentialsQueryOrFragment() {
    PublicSiteProperties site = completeSite();
    site.setSupportUrl("https://user:secret@support.example/path?token=x#anchor");

    assertThatThrownBy(() -> PortalPropertiesValidator.validatePublication(publicMode(), site, new LegalContentProperties()))
        .hasMessageContaining("lang.public.support-url");
  }

  @Test
  void rejectsInvalidSupportedRegionCode() {
    PublicSiteProperties site = completeSite();
    site.setSupportedRegions(java.util.List.of("CHN"));

    assertThatThrownBy(() -> PortalPropertiesValidator.validatePublication(publicMode(), site, new LegalContentProperties()))
        .hasMessageContaining("lang.public.supported-regions");
  }

  @Test
  void publicModeRequiresExactlyTheLegalSourceLocale() {
    PublicSiteProperties site = completeSite();
    site.setEnabledLocales(java.util.List.of("zh-CN", "en-US"));

    assertThatThrownBy(() -> PortalPropertiesValidator.validatePublication(publicMode(), site, new LegalContentProperties()))
        .hasMessageContaining("lang.public.enabled-locales");
  }

  @Test
  void rejectsUnknownLegalFormatAndOutOfRangeLimits() {
    LegalContentProperties legal = new LegalContentProperties();
    legal.setSourceFormat(null);
    legal.setMaxInputBytes(16 * 1024L - 1);
    legal.setMaxOutputBytes(1024 * 1024L + 1);
    legal.setCacheTtl(Duration.ofSeconds(9));

    assertThatThrownBy(() -> PortalPropertiesValidator.validatePublication(new PublicationProperties(), new PublicSiteProperties(), legal))
        .hasMessageContaining("lang.legal");
  }

  private static PublicationProperties publicMode() {
    PublicationProperties publication = new PublicationProperties();
    publication.setMode(PublicationMode.PUBLIC);
    return publication;
  }

  private static PublicSiteProperties completeSite() {
    PublicSiteProperties site = new PublicSiteProperties();
    site.setSiteUrl("https://portal.example");
    site.setSupportUrl("https://support.portal.example/contact");
    site.setSupportedRegions(java.util.List.of("CN"));
    site.setEnabledLocales(java.util.List.of("zh-CN"));
    return site;
  }

  private static PublicSiteProperties bindSite(Map<String, String> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bind("lang.public", PublicSiteProperties.class)
        .orElseThrow(() -> new AssertionError("公开配置应可绑定"));
  }
}
