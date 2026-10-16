package co.gov.dane.edi.util;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Deriva la columna de respuesta desde FRM_CAP_PERIODO.NUMERAL, como hacía el PHP: "C07A." responde
 * en la columna C07A y "B02*." en la columna B02. Los numerales de 2026 no traen el cero de la
 * columna: "C7a." también responde en C07A.
 */
public final class ColumnCodeResolver {

  private static final Pattern COLUMN_CODE = Pattern.compile("[A-Z][0-9A-Z_]{1,29}");
  private static final Pattern NUMERAL_DECORATION = Pattern.compile("[\\s.*]");
  // "C7a." es C07A en la tabla: los numerales de 2026 no traen el cero que sí tienen las columnas.
  private static final Pattern SINGLE_DIGIT_NUMBER =
      Pattern.compile("^(?<letter>[A-Z])(?<digit>\\d)(?!\\d)");
  private static final String WHOLE_QUESTION_MARK = "*.";

  private ColumnCodeResolver() {}

  public static Optional<String> fromNumeral(String numeral) {
    if (numeral == null) {
      return Optional.empty();
    }
    String candidate = NUMERAL_DECORATION.matcher(numeral).replaceAll("").toUpperCase(Locale.ROOT);
    candidate = SINGLE_DIGIT_NUMBER.matcher(candidate).replaceFirst("${letter}0${digit}");
    return COLUMN_CODE.matcher(candidate).matches() ? Optional.of(candidate) : Optional.empty();
  }

  /** Etiqueta visible del literal: "A." para "C01A."; el numeral completo si termina en "*.". */
  public static String displayLabel(String numeral) {
    String stripped = numeral.strip();
    if (stripped.endsWith(WHOLE_QUESTION_MARK)) {
      return stripped.substring(0, stripped.length() - WHOLE_QUESTION_MARK.length());
    }
    return stripped.length() <= 2 ? stripped : stripped.substring(stripped.length() - 2);
  }
}
