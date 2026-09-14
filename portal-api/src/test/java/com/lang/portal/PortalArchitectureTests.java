package com.lang.portal;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class PortalArchitectureTests {

  private final JavaClasses classes =
      new ClassFileImporter().importPackages("com.lang.portal");

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
}
