package co.gov.dane.edi.repository;

import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.model.AdminAccount;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAccountRepository {

  private static final String FIND_BY_USERNAME =
      "SELECT COD_USUA, CLAVE, ROL, COD_DPTO FROM GRAL_USUARIOS_ADM"
          + " WHERE UPPER(COD_USUA) = UPPER(:username) AND PROY LIKE :project";

  private final NamedParameterJdbcTemplate jdbc;
  private final SurveyProperties survey;

  public AdminAccountRepository(NamedParameterJdbcTemplate jdbc, SurveyProperties survey) {
    this.jdbc = jdbc;
    this.survey = survey;
  }

  public Optional<AdminAccount> findByUsername(String username) {
    return jdbc
        .query(
            FIND_BY_USERNAME,
            Map.of("username", username, "project", survey.code() + "%"),
            (row, index) ->
                new AdminAccount(
                    row.getString("COD_USUA"),
                    row.getString("CLAVE"),
                    row.getInt("ROL"),
                    row.getString("COD_DPTO")))
        .stream()
        .findFirst();
  }
}
