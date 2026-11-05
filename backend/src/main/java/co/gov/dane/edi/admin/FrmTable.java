package co.gov.dane.edi.admin;

import java.util.List;

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
  PREGUNTA("FRM_PREGUNTA", List.of("COD_PRE", "PREGUNTA", "TIPO"), List.of("PREGUNTA")),
  PRE_GRUPO("FRM_PRE_GRUPO", List.of("COD_PRE_GRUPO", "GRUPO", "ORDEN", "COD_PRE"), List.of()),
  CAP_PROY(
      "FRM_CAP_PROY",
      List.of("COD_CAP_PROY", "CAPITULO", "NUMERAL", "COD_PRE_GRUPO", "PROY", "ANIO"),
      List.of("COD_PRE_GRUPO")),
  CAP_PERIODO(
      "FRM_CAP_PERIODO",
      List.of("ID_PERIODO", "ANIO", "PROY", "COD_CAP_PROY", "NUMERAL"),
      List.of()),
  SALTO("FRM_SALTO", List.of("COD_SAL", "COD_CAP_PROY", "SALTO", "VALOR_SALTO"), List.of()),
  OBJ_PRE(
      "FRM_OBJ_PRE",
      List.of("COD_OBJ_PRE", "COD_GRUPO", "GRUPO", "COD_OBJ", "COD_CAP_PROY"),
      List.of()),
  RTA_GRUPO("FRM_RTA_GRUPO", List.of("COD_GRUPO", "GRUPO", "ORDEN", "VALOR", "COD_RTA"), List.of()),
  ENCABEZADO(
      "FRM_ENCABEZADO",
      List.of("COD_ENC", "COD_CAP_PROY", "TIPO", "ENCABEZADO", "ANIO"),
      List.of("ENCABEZADO")),
  // COD_AYU es una referencia a GRAL_AYUDAS.COD (la definición): va en la fila, pero ni se genera
  // ni se corrige; lo corregible es solo la palabra enlazada (TEXTO).
  PRE_AYUDA(
      "FRM_PRE_AYUDA",
      List.of("COD_PRE_AYU", "COD_CAP_PROY", "COD_AYU", "TEXTO"),
      List.of("TEXTO")),
  ENC_AYUDA(
      "FRM_ENC_AYUDA", List.of("COD_ENC_AYU", "COD_ENC", "COD_AYU", "TEXTO"), List.of("TEXTO"));

  private final String tableName;
  private final List<String> columns;
  private final List<String> correctableColumns;

  FrmTable(String tableName, List<String> columns, List<String> correctableColumns) {
    this.tableName = tableName;
    this.columns = columns;
    this.correctableColumns = correctableColumns;
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

  /** Columnas que un paquete de corrección puede actualizar en una fila que ya existe. */
  public List<String> correctableColumns() {
    return correctableColumns;
  }
}
