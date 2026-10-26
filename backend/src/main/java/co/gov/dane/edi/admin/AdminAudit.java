package co.gov.dane.edi.admin;

import java.util.List;
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

  public void record(String user, String survey, int year, String operation, List<String> codes) {
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
