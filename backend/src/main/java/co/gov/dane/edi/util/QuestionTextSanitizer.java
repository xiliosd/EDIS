package co.gov.dane.edi.util;

import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.web.util.HtmlUtils;

/**
 * Los textos heredados mezclan HTML y entidades (&oacute;). La API los entrega como texto plano y
 * el front los muestra escapados, así ningún texto administrable se interpreta como HTML.
 */
public final class QuestionTextSanitizer {

  private static final Pattern LINE_BREAK_TAG = Pattern.compile("(?i)<br\\s*/?>");
  private static final Pattern ANY_TAG = Pattern.compile("<[^>]*>");
  private static final Pattern REPEATED_SPACES = Pattern.compile("[ \\t\\x0B\\f\\r\\u00A0]+");

  private QuestionTextSanitizer() {}

  public static String toPlainText(String legacyText) {
    if (legacyText == null) {
      return "";
    }
    // Primero se quitan las etiquetas y luego se decodifica: "&lt;b&gt;" debe quedar como texto.
    String withLineBreaks = LINE_BREAK_TAG.matcher(legacyText).replaceAll("\n");
    String withoutTags = ANY_TAG.matcher(withLineBreaks).replaceAll("");
    String decoded = HtmlUtils.htmlUnescape(withoutTags);
    return REPEATED_SPACES
        .matcher(decoded)
        .replaceAll(" ")
        .lines()
        .map(String::strip)
        .collect(Collectors.joining("\n"))
        .strip();
  }
}
