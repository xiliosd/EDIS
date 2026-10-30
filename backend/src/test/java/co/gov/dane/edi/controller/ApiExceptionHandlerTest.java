package co.gov.dane.edi.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.gov.dane.edi.service.error.ResourceNotFoundException;
import co.gov.dane.edi.service.error.TooManyLoginAttemptsException;
import co.gov.dane.edi.service.error.YearCreationFailedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ApiExceptionHandlerTest.FailingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandlerTest.FailingController.class)
class ApiExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void notFoundIsProblemDetailInSpanish() throws Exception {
    mockMvc
        .perform(get("/test/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
        .andExpect(jsonPath("$.detail").value("El capítulo solicitado no existe."));
  }

  @Test
  void tooManyAttemptsAnnouncesRetryAfterSeconds() throws Exception {
    mockMvc
        .perform(get("/test/locked"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "900"))
        .andExpect(jsonPath("$.title").value("Demasiados intentos"));
  }

  @Test
  void databaseFailureHidesInternals() throws Exception {
    mockMvc
        .perform(get("/test/database"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().string(not(containsString("servidor-secreto"))))
        .andExpect(content().string(not(containsString("ORA-"))));
  }

  @Test
  void unexpectedErrorHidesInternals() throws Exception {
    mockMvc
        .perform(get("/test/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado."))
        .andExpect(content().string(not(containsString("detalle interno"))));
  }

  @Test
  void failedYearCreationExplainsTheCause() throws Exception {
    mockMvc
        .perform(get("/test/year-creation"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.title").value("No se creó el año"))
        .andExpect(jsonPath("$.detail").value("No fue posible guardar el archivo de reglas."))
        .andExpect(content().string(not(containsString("ruta-interna"))));
  }

  @Test
  void validationErrorsAreListedPerField() throws Exception {
    mockMvc
        .perform(
            post("/test/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Datos inválidos"))
        .andExpect(jsonPath("$.errors.username").value("Ingrese su usuario"));
  }

  record Payload(@NotBlank(message = "Ingrese su usuario") String username) {}

  @RestController
  static class FailingController {

    @GetMapping("/test/not-found")
    void notFound() {
      throw new ResourceNotFoundException("El capítulo solicitado no existe.");
    }

    @GetMapping("/test/locked")
    void locked() {
      throw new TooManyLoginAttemptsException(Duration.ofMinutes(15));
    }

    @GetMapping("/test/database")
    void database() {
      throw new DataAccessResourceFailureException("ORA-12541 host=servidor-secreto");
    }

    @GetMapping("/test/unexpected")
    void unexpected() {
      throw new IllegalStateException("detalle interno");
    }

    @GetMapping("/test/year-creation")
    void yearCreation() {
      throw new YearCreationFailedException(
          "No fue posible guardar el archivo de reglas.",
          new java.io.UncheckedIOException(new java.io.IOException("ruta-interna")));
    }

    @PostMapping("/test/validated")
    void validated(@Valid @RequestBody Payload payload) {
      // Solo dispara la validación.
    }
  }
}
