package com.lang.portal.base.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/portal/api/test-validation", produces = MediaType.APPLICATION_JSON_VALUE)
public class TestValidationController {

  @GetMapping
  public String query(@RequestParam("name") String name, @RequestParam("age") int age) {
    if (name == null || name.isBlank() || age < 1) {
      throw new com.lang.portal.base.exception.PortalException(
          com.lang.portal.base.exception.PortalErrorCode.INVALID_ARGUMENT);
    }
    return name + age;
  }

  @PostMapping
  public String body(@Valid @RequestBody NameForm form) {
    return form.name();
  }

  public record NameForm(@NotBlank String name) {}
}
