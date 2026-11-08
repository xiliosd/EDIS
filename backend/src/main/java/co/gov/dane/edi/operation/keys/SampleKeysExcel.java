package co.gov.dane.edi.operation.keys;

import co.gov.dane.edi.operation.excel.XlsxSheet;
import co.gov.dane.edi.operation.keys.SampleKeysRepository.CredentialRow;
import java.util.ArrayList;
import java.util.List;

/** Hoja "Claves" para la carta al coordinador (datos personales y claves: {@code no-store}). */
public final class SampleKeysExcel {

  private static final List<String> HEADERS =
      List.of("Entidad", "Código entidad", "Cédula", "Nombre", "Usuario", "Clave");

  private SampleKeysExcel() {}

  public static XlsxSheet sheet(
      List<CredentialRow> rows, String surveyCode, int year, int departmentCode) {
    List<List<Object>> values = new ArrayList<>();
    for (CredentialRow row : rows) {
      List<Object> cells = new ArrayList<>();
      cells.add(row.entityName());
      cells.add(row.entityCode());
      // Texto (no número): conserva la cédula tal cual; XlsxWriter nunca la vuelve fórmula.
      cells.add(row.respondentCode());
      cells.add(row.name());
      cells.add(row.login());
      cells.add(row.password());
      values.add(cells);
    }
    String title = "Claves - " + surveyCode + " " + year + " - departamento " + departmentCode;
    return new XlsxSheet("Claves", title, HEADERS, values);
  }
}
