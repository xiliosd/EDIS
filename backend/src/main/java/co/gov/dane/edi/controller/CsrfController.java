package co.gov.dane.edi.controller;

import co.gov.dane.edi.model.CsrfTokenResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class CsrfController {

  /** Leer el token obliga a Spring a emitir la cookie XSRF-TOKEN que el front reenvía. */
  @GetMapping("/csrf")
  public CsrfTokenResponse csrf(CsrfToken csrfToken) {
    return new CsrfTokenResponse(csrfToken.getToken());
  }
}
