package co.gov.dane.edi.admin;

import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Auditoría de los cambios administrativos: una línea estructurada por cambio, con usuario,
 * encuesta, año, operación y códigos afectados. Nunca texto de preguntas ni datos personales.
 */
@Component
public class AdminAudit {

  private static final Logger AUDIT = LoggerFactory.getLogger("edi.admin.audit");

  private static final Pattern OPERATION = Pattern.compile("[a-z][a-z0-9-]{0,40}");
  private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_-]{1,40}");

  /**
   * Operación y códigos deben ser claves fijas: así ningún texto libre (de preguntas o de personas)
   * puede llegar a la auditoría. Se valida aquí para cubrir a todos los llamadores.
   */
  public static void validate(String operation, List<String> codes) {
    if (operation == null || !OPERATION.matcher(operation).matches()) {
      throw new IllegalArgumentException("Operación de auditoría inválida");
    }
    if (codes == null) {
      throw new IllegalArgumentException("Códigos de auditoría inválidos");
    }
    for (String code : codes) {
      if (code == null || !CODE.matcher(code).matches()) {
        throw new IllegalArgumentException("Código de auditoría inválido");
      }
    }
  }

  public void record(String user, String survey, int year, String operation, List<String> codes) {
    validate(operation, codes);
    AUDIT.info(
        "usuario={} encuesta={} anio={} operacion={} codigos={}",
        clean(user),
        clean(survey),
        year,
        clean(operation),
        codes.stream().map(AdminAudit::clean).toList());
  }

  /** Evita que un valor con saltos de línea falsifique otra línea de auditoría. */
  private static String clean(String value) {
    return value == null ? "-" : value.replaceAll("[\\p{Cntrl}\\s]+", "_");
  }
}
