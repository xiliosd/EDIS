package co.gov.dane.edi.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Los 254 ítems reales de EDIN 2026 deben resolver a la columna que usaba Tabla() en el PHP. */
class ColumnCodeResolverReferenceTest {

  private static final Path REFERENCE = Path.of("..", "db", "reference", "frm-2026-orden.csv");

  @Test
  void everyRealNumeralResolvesToItsLegacyColumn() throws IOException {
    List<String> lines = Files.readAllLines(REFERENCE, StandardCharsets.UTF_8);
    assertThat(lines.get(0)).startsWith("PAGINA,POSICION,NUMERAL,COLUMNA");
    List<String> rows = lines.subList(1, lines.size());
    assertThat(rows).hasSize(254);
    for (String row : rows) {
      String[] fields = row.split(",", -1);
      assertThat(ColumnCodeResolver.fromNumeral(fields[2]))
          .as("numeral %s de la página %s", fields[2], fields[0])
          .contains(fields[3]);
    }
  }
}
