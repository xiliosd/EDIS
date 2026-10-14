package co.gov.dane.edi.repository;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.RespondentAccount;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RespondentAccountRepository {

  private final NamedParameterJdbcTemplate jdbc;
  private final SurveyProperties survey;

  public RespondentAccountRepository(NamedParameterJdbcTemplate jdbc, SurveyProperties survey) {
    this.jdbc = jdbc;
    this.survey = survey;
  }

  public Optional<RespondentAccount> findByLogin(String login) {
    // Acceso heredado: TC1 = 1 (diligencia web) y ATV = 1 (activo).
    String sql =
        "SELECT C_C, COD_ENT, PSW, TC1, ATV FROM "
            + survey.sampleTableName()
            + " WHERE LGN = :login AND ANIO = :year";
    return jdbc
        .query(
            sql,
            Map.of("login", login, "year", survey.yearText()),
            (row, index) ->
                new RespondentAccount(
                    row.getString("C_C"),
                    row.getString("COD_ENT"),
                    row.getString("PSW"),
                    row.getInt("TC1") == 1 && row.getInt("ATV") == 1))
        .stream()
        .findFirst();
  }
}
