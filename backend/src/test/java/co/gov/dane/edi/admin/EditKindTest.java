package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class EditKindTest {

  /** Las operaciones de edición que una corrección puede auditar. */
  private static final List<String> CORRECTABLE_OPERATIONS =
      List.of(
          "editar-texto",
          "editar-encabezado",
          "editar-ayuda",
          "editar-salto",
          "borrar-salto",
          "editar-validacion",
          "borrar-validacion",
          "editar-caso",
          "borrar-caso",
          "importar-reglas");

  @Test
  void correctionOperationKeepsTheAuditKeyShape() {
    Pattern shape = Pattern.compile("^[a-z][a-z0-9-]{0,40}$");
    for (String operation : CORRECTABLE_OPERATIONS) {
      String corrected = EditKind.correctionOperation(operation);
      assertThat(corrected).startsWith("corregir-").endsWith(operation).matches(shape);
      AdminAudit.validate(corrected, List.of());
    }
    assertThat(EditKind.correctionOperation("editar-texto")).isEqualTo("corregir-editar-texto");
  }
}
