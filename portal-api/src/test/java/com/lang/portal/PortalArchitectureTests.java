package com.lang.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class PortalArchitectureTests {

  private final JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.lang.portal");

  @Test
  void importedClassesComeOnlyFromProductionOutput() {
    assertThat(classes).isNotEmpty();
    assertThat(classes).allSatisfy(javaClass ->
        assertThat(javaClass.getSource().orElseThrow().getUri().toString())
            .doesNotContain("/test-classes/"));
  }

  @Test
  void webReturnRuleStillRejectsUpstreamTypes() {
    JavaClasses violations = new ClassFileImporter().importPackages("com.lang.portal.web.account.aggregation");
    assertThatThrownBy(() -> webReturnRule().check(violations))
        .isInstanceOf(AssertionError.class).hasMessageContaining("上游日志与传输类型");
  }

  @Test
  void controllersAndServicesMustNotDependOnUpstreamDtoOrTransport() {
    ArchRuleDefinition.noClasses()
        .that().resideInAnyPackage("..web..", "..service..", "..controller..")
        .and().resideOutsideOfPackages("..upstream.newapi..")
        .should().dependOnClassesThat().resideInAnyPackage("..upstream.newapi.dto..", "..upstream.newapi.transport..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void nonPublicControllersMustDeclareProtection() {
    var rule = ArchRuleDefinition.classes()
        .that().haveSimpleNameEndingWith("Controller")
        .and().resideInAPackage("com.lang.portal.web..")
        .and().haveSimpleNameNotContaining("PublicConfig")
        .and().haveSimpleNameNotContaining("CatalogController")
        .and().haveSimpleNameNotContaining("TestProtected")
        .should().beAnnotatedWith(com.lang.portal.base.security.ProtectedEndpoint.class)
        .orShould().beAnnotatedWith(org.springframework.security.access.prepost.PreAuthorize.class);
    rule.allowEmptyShould(true).check(classes);
  }

  @Test
  void aggregationBaseMustNotDependOnWebOrUpstream() {
    ArchRuleDefinition.noClasses()
        .that().resideInAPackage("com.lang.portal.base.aggregation..")
        .should().dependOnClassesThat().resideInAnyPackage("..web..", "..upstream..")
        .check(classes);
  }

  @Test
  void webLayerMustNotReturnUpstreamLogOrDtoTypes() {
    webReturnRule().check(classes);
  }

  private static com.tngtech.archunit.lang.ArchRule webReturnRule() {
    return ArchRuleDefinition.noMethods()
        .that().areDeclaredInClassesThat().resideInAnyPackage("..web..")
        .should().haveRawReturnType(new DescribedPredicate<JavaClass>("上游日志与传输类型") {
          @Override
          public boolean test(JavaClass javaClass) {
            String pkg = javaClass.getPackageName();
            return pkg.contains(".upstream.newapi.log")
                || pkg.contains(".upstream.newapi.dto")
                || pkg.contains(".upstream.newapi.transport");
          }
        })
        .allowEmptyShould(false);
  }
}
