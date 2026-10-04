package com.lang.portal.web.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class DashboardStatsArchitectureTests {

  private final JavaClasses classes =
      new ClassFileImporter().importPackages("com.lang.portal");

  @Test
  void dashboardDtoMustNotReferenceUpstreamTypes() {
    ArchRuleDefinition.noClasses()
        .that().resideInAPackage("com.lang.portal.web.dashboard..")
        .and().haveSimpleNameEndingWith("Dto")
        .or().haveSimpleNameEndingWith("Metric")
        .or().haveSimpleNameEndingWith("Data")
        .should().dependOnClassesThat().resideInAnyPackage("..upstream.newapi..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void dashboardControllerMustNotDependOnLogClient() {
    ArchRuleDefinition.noClasses()
        .that().haveSimpleName("DashboardStatsController")
        .should().dependOnClassesThat().haveSimpleName("NewApiLogClient")
        .allowEmptyShould(true)
        .check(classes);
  }
}
