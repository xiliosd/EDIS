package co.gov.dane.edi.util;

import java.nio.charset.Charset;
import java.util.BitSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Textos para la base real, cuyo juego de caracteres es WE8ISO8859P15 (Latin-9, un byte por
 * carácter, longitudes en BYTE). Oracle cambia por "?" sin error todo carácter que Latin-9 no tiene
 * (comillas tipográficas, rayas, viñetas, emojis): por eso, igual que el PHP heredado guarda
 * entidades, al escribir esos caracteres se convierten en la entidad numérica {@code &#N;} y la
 * lectura la decodifica. Las tildes, la ñ, la ü, el €, ¿ y ¡ sí existen en Latin-9 y van tal cual.
 *
 * <p>La longitud que cuenta frente a la columna es la del valor ya convertido: cada carácter de
 * Latin-9 ocupa un byte y cada entidad tantos bytes como caracteres tiene. Se mide aquí y no con el
 * juego de caracteres de la base de pruebas (AL32UTF8), que no es el de producción.
 */
public final class Latin9Text {

  private static final Charset LATIN_9 = Charset.forName("ISO-8859-15");

  /** Los 256 caracteres (todos del plano básico) que existen en Latin-9. */
  private static final BitSet LATIN_9_CHARACTERS = latin9Characters();

  private static final Pattern NUMERIC_REFERENCE =
      Pattern.compile("&#(?:[0-9]{1,7}|[xX][0-9a-fA-F]{1,6});");

  private Latin9Text() {}

  /**
   * El texto con cada carácter ajeno a Latin-9 como {@code &#N;} (decimal; un emoji, que en Java es
   * un par sustituto, da una sola entidad). Lo demás no cambia, ni siquiera {@code &}: escapar el
   * marcado es decisión de quien llama.
   */
  public static String encode(String text) {
    if (text == null) {
      return null;
    }
    StringBuilder encoded = null;
    int index = 0;
    while (index < text.length()) {
      int codePoint = text.codePointAt(index);
      int width = Character.charCount(codePoint);
      boolean kept = width == 1 && LATIN_9_CHARACTERS.get(codePoint);
      if (!kept && encoded == null) {
        encoded = new StringBuilder(text.length() + 16).append(text, 0, index);
      }
      if (encoded != null) {
        if (kept) {
          encoded.append((char) codePoint);
        } else {
          encoded.append("&#").append(codePoint).append(';');
        }
      }
      index += width;
    }
    return encoded == null ? text : encoded.toString();
  }

  /** Bytes que el texto ocupará en una columna Latin-9 una vez convertido con {@link #encode}. */
  public static int storedLength(String text) {
    // Todo lo que queda tras convertir es un carácter de Latin-9: un byte cada uno.
    return text == null ? 0 : encode(text).length();
  }

  /** Si el texto, ya convertido, cabe en una columna de {@code maxBytes} bytes. */
  public static boolean fits(String text, int maxBytes) {
    return storedLength(text) <= maxBytes;
  }

  /**
   * Decodifica solo las referencias numéricas {@code &#N;} y {@code &#xH;} (incluidas las de fuera
   * del plano básico). Las con nombre y las inválidas (sustitutos sueltos, más allá de U+10FFFF)
   * quedan como están.
   */
  public static String decodeNumericReferences(String text) {
    if (text == null || text.indexOf("&#") < 0) {
      return text;
    }
    Matcher matcher = NUMERIC_REFERENCE.matcher(text);
    StringBuilder decoded = new StringBuilder(text.length());
    while (matcher.find()) {
      int codePoint = numericValue(matcher.group());
      String replacement = codePoint < 0 ? matcher.group() : Character.toString(codePoint);
      matcher.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(decoded);
    return decoded.toString();
  }

  /** Punto de código de {@code &#N;} o {@code &#xH;}; -1 si no es un carácter válido. */
  private static int numericValue(String reference) {
    boolean hex = reference.charAt(2) == 'x' || reference.charAt(2) == 'X';
    String digits = reference.substring(hex ? 3 : 2, reference.length() - 1);
    if (digits.isEmpty()) {
      return -1;
    }
    int codePoint = Integer.parseInt(digits, hex ? 16 : 10);
    boolean valid =
        Character.isValidCodePoint(codePoint)
            && !(codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE);
    return valid ? codePoint : -1;
  }

  private static BitSet latin9Characters() {
    byte[] all = new byte[256];
    for (int value = 0; value < all.length; value++) {
      all[value] = (byte) value;
    }
    BitSet characters = new BitSet(0x10000);
    new String(all, LATIN_9).chars().forEach(characters::set);
    return characters;
  }
}
