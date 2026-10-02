package com.lang.portal.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class CatalogArchitectureTests {

  private static final List<Class<?>> WEB_DTOS =
      List.of(
          CatalogData.class,
          CatalogModel.class,
          CatalogPricing.class,
          CatalogModelDetails.class,
          CatalogCapabilities.class,
          CatalogDetailData.class,
          CatalogProvidersData.class,
          ModelProviderOption.class,
          EnhancedPricingItem.class,
          EnhancedPriceType.class,
          ModelModality.class);

  @Test
  void webDtosDoNotReferenceUpstreamTypes() {
    for (Class<?> dto : WEB_DTOS) {
      RecordComponent[] components = dto.getRecordComponents();
      if (components != null) {
        for (RecordComponent c : components) {
          assertThat(c.getType().getName())
              .doesNotStartWith("com.lang.portal.upstream.newapi");
        }
      }
      for (var field : dto.getDeclaredFields()) {
        assertThat(field.getType().getName())
            .doesNotStartWith("com.lang.portal.upstream.newapi");
      }
    }
  }

  @Test
  void catalogControllerIsGetOnly() {
    for (Method m : CatalogController.class.getDeclaredMethods()) {
      assertThat(m.isAnnotationPresent(PostMapping.class)).isFalse();
      assertThat(m.isAnnotationPresent(PutMapping.class)).isFalse();
      assertThat(m.isAnnotationPresent(PatchMapping.class)).isFalse();
      assertThat(m.isAnnotationPresent(DeleteMapping.class)).isFalse();
    }
    long getCount =
        java.util.Arrays.stream(CatalogController.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(GetMapping.class))
            .count();
    assertThat(getCount).isEqualTo(4);
  }
}
