package co.gov.dane.edi.service.error;

import java.util.Map;

/** Respuestas inválidas: columna → mensaje en español. */
public class AnswerValidationException extends RuntimeException {

  private final Map<String, String> fieldMessages;

  public AnswerValidationException(Map<String, String> fieldMessages) {
    super("Respuestas inválidas");
    this.fieldMessages = Map.copyOf(fieldMessages);
  }

  public Map<String, String> fieldMessages() {
    return fieldMessages;
  }
}
