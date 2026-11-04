package co.gov.dane.edi.support;

import java.util.List;

/**
 * Textos ficticios que rompen un script de SQL*Plus ingenuo: comillas, {@code &}, líneas "/" y ".",
 * comentarios, caracteres no ASCII, un emoji en el corte de 1000, controles, escapes JDBC y un
 * texto de casi 4000 bytes con un salto cada 3 caracteres.
 */
public final class HostileTexts {

  /** Emoji fuera del plano básico: en UTF-16 es un par sustituto. */
  public static final String EMOJI = "😀";

  /** Se guardan todos en VARCHAR2(4000) de Oracle; NUL va aparte por si Oracle no lo conserva. */
  public static final List<String> STORABLE =
      List.of(
          "O'Brien & <b>",
          "\n/\n.\n",
          "-- /* ;",
          "Señal ñ y acentos áé",
          "a".repeat(999) + EMOJI + "b",
          "línea\r\notra",
          "tab\u000Bvertical y \u007F borrar",
          "{fn x} y {call y} con \\ barra \\0041",
          "sep \u0085     fin",
          "ab\n".repeat(1333));

  /** El carácter NUL; la prueba de Oracle lo usa solo si Oracle lo guarda tal cual. */
  public static final String WITH_NUL = "antes\u0000despues";

  /** Todos los textos, incluido el NUL: para las pruebas que no pasan por Oracle. */
  public static List<String> all() {
    return java.util.stream.Stream.concat(STORABLE.stream(), java.util.stream.Stream.of(WITH_NUL))
        .toList();
  }

  private HostileTexts() {}
}
