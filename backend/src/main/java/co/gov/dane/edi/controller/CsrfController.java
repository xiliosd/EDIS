package co.gov.dane.edi.controller;

import co.gov.dane.edi.model.CsrfTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticación")
@RestController
@RequestMapping("/api/v1/auth")
public class CsrfController {

  /** Leer el token obliga a Spring a emitir la cookie XSRF-TOKEN que el front reenvía. */
  @Operation(summary = "Obtener token CSRF", description = "Emite la cookie XSRF-TOKEN.")
  @SecurityRequirements
  @GetMapping(value = "/csrf", produces = MediaType.APPLICATION_JSON_VALUE)
  public CsrfTokenResponse csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
    return new CsrfTokenResponse(csrfToken.getToken());
  }
}
