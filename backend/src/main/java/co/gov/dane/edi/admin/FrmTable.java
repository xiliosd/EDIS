package co.gov.dane.edi.admin;

/**
 * Tablas FRM_* con identificador propio. Los nombres de tabla y columna que llegan al SQL salen
 * solo de este enum, nunca de una entrada externa.
 */
public enum FrmTable {
  PREGUNTA("FRM_PREGUNTA", "COD_PRE"),
  PRE_GRUPO("FRM_PRE_GRUPO", "COD_PRE_GRUPO"),
  CAP_PROY("FRM_CAP_PROY", "COD_CAP_PROY"),
  CAP_PERIODO("FRM_CAP_PERIODO", "ID_PERIODO"),
  SALTO("FRM_SALTO", "COD_SAL"),
  OBJ_PRE("FRM_OBJ_PRE", "COD_OBJ_PRE"),
  RTA_GRUPO("FRM_RTA_GRUPO", "COD_GRUPO"),
  ENCABEZADO("FRM_ENCABEZADO", "COD_ENC"),
  PRE_AYUDA("FRM_PRE_AYUDA", "COD_AYU"),
  ENC_AYUDA("FRM_ENC_AYUDA", "COD_AYU");

  private final String tableName;
  private final String idColumn;

  FrmTable(String tableName, String idColumn) {
    this.tableName = tableName;
    this.idColumn = idColumn;
  }

  public String tableName() {
    return tableName;
  }

  public String idColumn() {
    return idColumn;
  }
}
