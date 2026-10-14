package co.gov.dane.edi.support;

import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/** Base de las pruebas de integración: una sola Oracle Free compartida por toda la ejecución. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class OracleIntegrationTest {

  protected static final OracleContainer ORACLE =
      new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"))
          .withStartupTimeout(Duration.ofMinutes(5));

  static {
    ORACLE.start();
  }

  @DynamicPropertySource
  static void registerOracleProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
    registry.add("spring.datasource.username", ORACLE::getUsername);
    registry.add("spring.datasource.password", ORACLE::getPassword);
    registry.add("app.survey.db-schema", () -> ORACLE.getUsername().toUpperCase(Locale.ROOT));
  }
}
