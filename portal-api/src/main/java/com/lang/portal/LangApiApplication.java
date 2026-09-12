package com.lang.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LangApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(LangApiApplication.class, args);
  }
}
