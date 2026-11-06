package co.gov.dane.edi.admin;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Tablas FRM_* con identificador propio. Los nombres de tabla y columna que llegan al SQL salen
 * solo de este enum, nunca de una entrada externa.
 *
 * <p>{@link #columns()} son las columnas reales (respaldo de DANE_DIRPEN, en {@code
 * db/dev/schema.sql}) (el id primero): solo esas entran en la fotografía del año y en {@code
 * frm.sql}. {@link #correctableColumns()} son las que una corrección sobre un año publicado puede
 * cambiar en una fila existente: los textos y el repunte de FRM_CAP_PROY que hace la copia al
 * escribir.
 */
public enum FrmTable {
  PREGUNTA(
      "FRM_PREGUNTA",
      List.of("COD_PRE", "PREGUNTA", "TIPO"),
      List.of("PREGUNTA"),
      Map.of("PREGUNTA", 1000, "TIPO", 1)),
  PRE_GRUPO(
      "FRM_PRE_GRUPO", List.of("COD_PRE_GRUPO", "GRUPO", "ORDEN", "COD_PRE"), List.of(), Map.of()),
  CAP_PROY(
      "FRM_CAP_PROY",
      List.of("COD_CAP_PROY", "CAPITULO", "NUMERAL", "COD_PRE_GRUPO", "PROY", "ANIO"),
      List.of("COD_PRE_GRUPO"),
      Map.of("CAPITULO", 2, "NUMERAL", 5, "PROY", 10, "ANIO", 4)),
  CAP_PERIODO(
      "FRM_CAP_PERIODO",
      List.of("ID_PERIODO", "ANIO", "PROY", "COD_CAP_PROY", "NUMERAL"),
      List.of(),
      Map.of("PROY", 10, "NUMERAL", 10)),
  SALTO(
      "FRM_SALTO", List.of("COD_SAL", "COD_CAP_PROY", "SALTO", "VALOR_SALTO"), List.of(), Map.of()),
  OBJ_PRE(
      "FRM_OBJ_PRE",
      List.of("COD_OBJ_PRE", "COD_GRUPO", "GRUPO", "COD_OBJ", "COD_CAP_PROY"),
      List.of(),
      Map.of()),
  RTA_GRUPO(
      "FRM_RTA_GRUPO",
      List.of("COD_GRUPO", "GRUPO", "ORDEN", "VALOR", "COD_RTA"),
      List.of(),
      Map.of("ORDEN", 2, "VALOR", 5)),
  ENCABEZADO(
      "FRM_ENCABEZADO",
      List.of("COD_ENC", "COD_CAP_PROY", "TIPO", "ENCABEZADO", "ANIO"),
      List.of("ENCABEZADO"),
      Map.of("TIPO", 1, "ENCABEZADO", 500)),
  // COD_AYU es una referencia a GRAL_AYUDAS.COD (la definición): va en la fila, pero ni se genera
  // ni se corrige; lo corregible es solo la palabra enlazada (TEXTO).
  PRE_AYUDA(
      "FRM_PRE_AYUDA",
      List.of("COD_PRE_AYU", "COD_CAP_PROY", "COD_AYU", "TEXTO"),
      List.of("TEXTO"),
      Map.of("TEXTO", 100)),
  ENC_AYUDA(
      "FRM_ENC_AYUDA",
      List.of("COD_ENC_AYU", "COD_ENC", "COD_AYU", "TEXTO"),
      List.of("TEXTO"),
      Map.of("TEXTO", 100));

  private final String tableName;
  private final List<String> columns;
  private final List<String> correctableColumns;
  private final Map<String, Integer> textColumnBytes;

  FrmTable(
      String tableName,
      List<String> columns,
      List<String> correctableColumns,
      Map<String, Integer> textColumnBytes) {
    this.tableName = tableName;
    this.columns = columns;
    this.correctableColumns = correctableColumns;
    this.textColumnBytes = textColumnBytes;
  }

  public String tableName() {
    return tableName;
  }

  public String idColumn() {
    return columns.get(0);
  }

  /** Columnas reales de la tabla, con el id primero. */
  public List<String> columns() {
    return columns;
  }

  /**
   * Longitud en bytes de una columna VARCHAR2 en la base real (WE8ISO8859P15, semántica BYTE,
   * confirmada en el diccionario el 2026-11-06); vacío si la columna es numérica. Un valor más
   * largo daría ORA-12899.
   */
  public OptionalInt maxBytes(String column) {
    Integer bytes = textColumnBytes.get(column);
    return bytes == null ? OptionalInt.empty() : OptionalInt.of(bytes);
  }

  /** Columnas que un paquete de corrección puede actualizar en una fila que ya existe. */
  public List<String> correctableColumns() {
    return correctableColumns;
  }
}
