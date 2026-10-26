package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AdminAuditTest {

  @Test
  void writesOneStructuredLineWithoutFreeText(CapturedOutput output) {
    new AdminAudit().record("admin1", "EDIN", 2027, "editar-texto", List.of("B06", "B07"));

    assertThat(output)
        .contains("edi.admin.audit")
        .contains("usuario=admin1 encuesta=EDIN anio=2027 operacion=editar-texto")
        .contains("codigos=[B06, B07]");
  }

  @Test
  void controlCharactersCannotForgeASecondLine(CapturedOutput output) {
    new AdminAudit().record("a\nFORJADA", "EDIN", 2027, "op\r\nx", List.of());

    assertThat(output.getAll().lines().filter(line -> line.startsWith("FORJADA"))).isEmpty();
  }
}
