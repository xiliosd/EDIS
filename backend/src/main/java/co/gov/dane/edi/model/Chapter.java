package co.gov.dane.edi.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Capítulos con preguntas del cuestionario. EDI y EDID comparten estas tablas: cada una tiene las
 * columnas FEX_EDI y FEX_EDID. Los nombres son fijos porque se concatenan en el SQL.
 */
public enum Chapter {
  B("Ubicación laboral", Modules.SOCIODEMOGRAPHIC, "EDIN_20_CAP_B_UBICA", List.of("B1")),
  C(
      "Experiencia personal de trabajo",
      Modules.ENVIRONMENT,
      "EDIN_20_CAP_C_EXP_PERS",
      List.of("C1", "C2")),
  D(
      "Gestión del talento humano",
      Modules.ENVIRONMENT,
      "EDIN_20_CAP_D_GEST_TAL",
      List.of("D1", "D2")),
  E(
      "Uso de la tecnología e información",
      Modules.PERFORMANCE,
      "EDIN_20_CAP_E_USO_TECN",
      List.of("E1")),
  F("Gestión de la entidad", Modules.PERFORMANCE, "EDIN_20_CAP_F_GEST_ENT", List.of("F1")),
  G("Rendición de cuentas", Modules.PERFORMANCE, "EDIN_20_CAP_G_REN_CUEN", List.of("G1")),
  H(
      "Prevención de prácticas irregulares",
      Modules.PERFORMANCE,
      "EDIN_20_CAP_H_PREV_PRACT",
      List.of("H1"));

  private final String title;
  private final String module;
  private final String tableName;
  private final List<String> pageCodes;

  Chapter(String title, String module, String tableName, List<String> pageCodes) {
    this.title = title;
    this.module = module;
    this.tableName = tableName;
    this.pageCodes = pageCodes;
  }

  public static Optional<Chapter> fromCode(String code) {
    if (code == null) {
      return Optional.empty();
    }
    String normalized = code.strip().toUpperCase(Locale.ROOT);
    return Arrays.stream(values()).filter(c -> c.name().equals(normalized)).findFirst();
  }

  public String code() {
    return name();
  }

  public String title() {
    return title;
  }

  public String module() {
    return module;
  }

  public String tableName() {
    return tableName;
  }

  public List<String> pageCodes() {
    return pageCodes;
  }

  public ChapterSummary toSummary() {
    return new ChapterSummary(code(), title, module);
  }

  private static final class Modules {
    static final String SOCIODEMOGRAPHIC = "Información sociodemográfica";
    static final String ENVIRONMENT = "Ambiente institucional";
    static final String PERFORMANCE = "Desempeño institucional";
  }
}
