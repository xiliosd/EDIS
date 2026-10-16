package co.gov.dane.edi.service;

import co.gov.dane.edi.rules.RuleErrorCodes;
import java.util.Map;

/** Única tabla de mensajes del backend; el frontend tiene la suya con los mismos textos. */
public final class AnswerErrorMessages {

  private static final Map<String, String> MESSAGES =
      Map.of(
          RuleErrorCodes.REQUIRED, "Responda esta pregunta.",
          RuleErrorCodes.INVALID_TYPE, "El valor no tiene el formato esperado.",
          RuleErrorCodes.OUT_OF_RANGE, "El valor está fuera del rango permitido.",
          RuleErrorCodes.TOO_LONG, "La respuesta es demasiado larga.",
          RuleErrorCodes.INVALID_OPTION, "Seleccione una de las opciones.",
          RuleErrorCodes.UNKNOWN_QUESTION, "Pregunta desconocida.");

  private AnswerErrorMessages() {}

  public static String messageFor(String code) {
    return MESSAGES.getOrDefault(code, "Respuesta inválida.");
  }
}
