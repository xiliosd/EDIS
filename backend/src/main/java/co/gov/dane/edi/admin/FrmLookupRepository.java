package co.gov.dane.edi.admin;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Consultas de pertenencia: a qué pregunta y página de una encuesta y año apunta una clave. */
@Repository
public class FrmLookupRepository {

  /** Pregunta (FRM_CAP_PROY) tal como la ve un año: su página y su numeral. */
  public record CapProyRef(long capProyId, String pageCode, String numeral) {}

  private final JdbcTemplate jdbc;

  public FrmLookupRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Vacío si el FRM_CAP_PROY no tiene periodo en esa encuesta y año. */
  public Optional<CapProyRef> capProyOfYear(long capProyId, String project, int year) {
    List<CapProyRef> found =
        jdbc.query(
            "SELECT C.COD_CAP_PROY, C.CAPITULO, P.NUMERAL FROM FRM_CAP_PROY C"
                + " JOIN FRM_CAP_PERIODO P ON P.COD_CAP_PROY = C.COD_CAP_PROY"
                + " WHERE C.COD_CAP_PROY = ? AND P.PROY = ? AND P.ANIO = ?",
            (rows, rowNumber) ->
                new CapProyRef(rows.getLong(1), rows.getString(2), rows.getString(3)),
            capProyId,
            project,
            year);
    return found.stream().findFirst();
  }

  /** FRM_CAP_PROY del encabezado cuyo ANIO es el indicado. */
  public Optional<Long> capProyOfHeader(long codEnc, String yearText) {
    return first(
        "SELECT COD_CAP_PROY FROM FRM_ENCABEZADO WHERE COD_ENC = ? AND ANIO = ?", codEnc, yearText);
  }

  public Optional<Long> capProyOfHelp(long codAyu) {
    return first("SELECT COD_CAP_PROY FROM FRM_PRE_AYUDA WHERE COD_AYU = ?", codAyu);
  }

  /** Número de GRUPO (bloque de orden) de cada FRM_CAP_PROY indicado. */
  public Map<Long, Integer> groupNumbers(Collection<Long> capProyIds) {
    Map<Long, Integer> groups = new HashMap<>();
    if (capProyIds.isEmpty()) {
      return groups;
    }
    String placeholders = String.join(",", Collections.nCopies(capProyIds.size(), "?"));
    jdbc.query(
        "SELECT C.COD_CAP_PROY, G.GRUPO FROM FRM_CAP_PROY C"
            + " JOIN FRM_PRE_GRUPO G ON G.COD_PRE_GRUPO = C.COD_PRE_GRUPO"
            + " WHERE C.COD_CAP_PROY IN ("
            + placeholders
            + ")",
        rows -> {
          groups.put(rows.getLong(1), rows.getInt(2));
        },
        capProyIds.toArray());
    return groups;
  }

  private Optional<Long> first(String sql, Object... arguments) {
    return jdbc.query(sql, (rows, rowNumber) -> rows.getLong(1), arguments).stream().findFirst();
  }
}
