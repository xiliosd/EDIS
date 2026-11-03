package co.gov.dane.edi.rules;

import java.util.List;

/**
 * Un hallazgo del análisis. {@code element} es {@code regla:<id>}, {@code validacion:<COL>}, {@code
 * caso:<id>}, {@code pregunta:<COL>} o {@code archivo}; el mensaje nombra códigos y valores, nunca
 * el texto de las preguntas.
 */
public record Finding(String code, String element, String message, List<String> questions) {

  public Finding {
    questions = questions == null ? List.of() : List.copyOf(questions);
  }

  /** Identidad para comparar informes: el mensaje puede cambiar sin que el hallazgo sea otro. */
  Finding identity() {
    return new Finding(code, element, "", questions);
  }
}
