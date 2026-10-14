package co.gov.dane.edi.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints mínimos para verificar las reglas de autorización por ruta. Solo existe en pruebas. */
@RestController
class SecurityProbeController {

  @GetMapping("/api/v1/survey/probe")
  String surveyProbe() {
    return "ok";
  }

  @PostMapping("/api/v1/survey/probe")
  String surveyProbeWrite() {
    return "ok";
  }

  @GetMapping("/api/v1/admin/probe")
  String adminProbe() {
    return "ok";
  }

  /** Simula una autorización a nivel de método que falla dentro del controlador. */
  @GetMapping("/api/v1/survey/probe-denied")
  String deniedProbe() {
    throw new AccessDeniedException("denegado desde el controlador");
  }
}
