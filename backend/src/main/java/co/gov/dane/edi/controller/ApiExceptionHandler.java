package co.gov.dane.edi.controller;

import co.gov.dane.edi.service.error.AnswerValidationException;
import co.gov.dane.edi.service.error.ConflictException;
import co.gov.dane.edi.service.error.InvalidCredentialsException;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import co.gov.dane.edi.service.error.TooManyLoginAttemptsException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  ProblemDetail handleNotFound(ResourceNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", exception.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  ProblemDetail handleConflict(ConflictException exception) {
    return problem(HttpStatus.CONFLICT, "Conflicto", exception.getMessage());
  }

  @ExceptionHandler(AnswerValidationException.class)
  ProblemDetail handleInvalidAnswers(AnswerValidationException exception) {
    ProblemDetail body =
        problem(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Respuestas inválidas",
            "Revise las preguntas marcadas.");
    body.setProperty("errors", new TreeMap<>(exception.fieldMessages()));
    return body;
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
    return problem(HttpStatus.UNAUTHORIZED, "Credenciales inválidas", exception.getMessage());
  }

  @ExceptionHandler(TooManyLoginAttemptsException.class)
  ResponseEntity<ProblemDetail> handleTooManyAttempts(TooManyLoginAttemptsException exception) {
    ProblemDetail body =
        problem(
            HttpStatus.TOO_MANY_REQUESTS,
            "Demasiados intentos",
            "Superó el número de intentos permitidos. Intente de nuevo más tarde.");
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfter().toSeconds()))
        .body(body);
  }

  @ExceptionHandler(DataAccessException.class)
  ProblemDetail handleDataAccess(DataAccessException exception) {
    LOGGER.error("Falla de acceso a la base de datos", exception);
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Servicio no disponible",
        "La base de datos no está disponible. Intente de nuevo en unos minutos.");
  }

  /** Se relanzan para que ExceptionTranslationFilter responda 401/403 y no un 500. */
  @ExceptionHandler(AccessDeniedException.class)
  void rethrowAccessDenied(AccessDeniedException exception) {
    throw exception;
  }

  @ExceptionHandler(AuthenticationException.class)
  void rethrowAuthentication(AuthenticationException exception) {
    throw exception;
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception exception) {
    LOGGER.error("Error inesperado", exception);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ocurrió un error inesperado.");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail body =
        problem(HttpStatus.BAD_REQUEST, "Datos inválidos", "Revise los campos marcados.");
    Map<String, String> errors = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    body.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(body);
  }

  static ProblemDetail problem(HttpStatus status, String title, String detail) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    return problemDetail;
  }
}
