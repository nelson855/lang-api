package com.lang.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ConfigurationIsolationTests {

  @Autowired
  private Environment environment;

  @Test
  void testProfileLoadsTestSpecificValue() {
    assertThat(environment.getProperty("lang.env")).isEqualTo("test");
  }

  @Test
  void noYamlConfigurationExists() {
    assertThat(new File("src/main/resources/application.yml")).doesNotExist();
    assertThat(new File("src/main/resources/application.yaml")).doesNotExist();
  }

  @Test
  void sharedConfigurationDoesNotActivateAnyProfile() throws IOException {
    String shared = Files.readString(new File("src/main/resources/application.properties").toPath());
    assertThat(shared).doesNotContain("spring.profiles.active");
  }
}
