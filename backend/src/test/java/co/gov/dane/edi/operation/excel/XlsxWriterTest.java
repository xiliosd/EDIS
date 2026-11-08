package co.gov.dane.edi.operation.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class XlsxWriterTest {

  private final XlsxWriter writer = new XlsxWriter();

  private XSSFWorkbook read(byte[] content) throws IOException {
    return new XSSFWorkbook(new ByteArrayInputStream(content));
  }

  private XlsxSheet sheetWith(List<Object> row) {
    return new XlsxSheet(
        "Datos", "Título ficticio", List.of("A", "B", "C", "D", "E"), List.of(row));
  }

  @Test
  void textThatLooksLikeAFormulaStaysText() throws IOException {
    byte[] content =
        writer.write(
            List.of(sheetWith(Arrays.asList("=SUMA(1;2)", "@Ficticia", "+1", "-2", null))));
    try (XSSFWorkbook workbook = read(content)) {
      Sheet sheet = workbook.getSheet("Datos");
      Cell formulaLike = sheet.getRow(2).getCell(0);
      assertThat(formulaLike.getCellType()).isEqualTo(CellType.STRING);
      assertThat(formulaLike.getStringCellValue()).isEqualTo("=SUMA(1;2)");
      assertThat(sheet.getRow(2).getCell(1).getCellType()).isEqualTo(CellType.STRING);
      assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("@Ficticia");
      assertThat(sheet.getRow(2).getCell(2).getCellType()).isEqualTo(CellType.STRING);
      assertThat(sheet.getRow(2).getCell(4)).isNull();
    }
  }

  @Test
  void titleRowThenFrozenHeaders() throws IOException {
    byte[] content = writer.write(List.of(sheetWith(List.of("x", "y", "z", "w", "v"))));
    try (XSSFWorkbook workbook = read(content)) {
      Sheet sheet = workbook.getSheet("Datos");
      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Título ficticio");
      assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("A");
      assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("E");
      assertThat(sheet.getPaneInformation()).isNotNull();
      assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
      assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 2);
    }
  }

  @Test
  void numbersAndDecimalsAreNumeric() throws IOException {
    byte[] content =
        writer.write(
            List.of(sheetWith(List.of(7L, 3, new BigDecimal("62.50"), 1.5d, new BigDecimal("0")))));
    try (XSSFWorkbook workbook = read(content)) {
      Sheet sheet = workbook.getSheet("Datos");
      Cell decimal = sheet.getRow(2).getCell(2);
      assertThat(sheet.getRow(2).getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(sheet.getRow(2).getCell(0).getNumericCellValue()).isEqualTo(7d);
      assertThat(decimal.getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(decimal.getNumericCellValue()).isEqualTo(62.5d);
      assertThat(decimal.getCellStyle().getDataFormatString()).isEqualTo("0.00");
    }
  }

  @Test
  void datesAreDateFormattedNumbers() throws IOException {
    byte[] content =
        writer.write(
            List.of(
                sheetWith(
                    List.of(
                        LocalDate.of(2026, 3, 10),
                        LocalDateTime.of(2026, 3, 10, 9, 30),
                        "a",
                        "b",
                        "c"))));
    try (XSSFWorkbook workbook = read(content)) {
      Sheet sheet = workbook.getSheet("Datos");
      Cell date = sheet.getRow(2).getCell(0);
      Cell dateTime = sheet.getRow(2).getCell(1);
      assertThat(DateUtil.isCellDateFormatted(date)).isTrue();
      assertThat(date.getLocalDateTimeCellValue().toLocalDate())
          .isEqualTo(LocalDate.of(2026, 3, 10));
      assertThat(DateUtil.isCellDateFormatted(dateTime)).isTrue();
      assertThat(dateTime.getLocalDateTimeCellValue())
          .isEqualTo(LocalDateTime.of(2026, 3, 10, 9, 30));
    }
  }

  @Test
  void sheetNamesAreSanitizedAndTruncated() throws IOException {
    XlsxSheet awkward =
        new XlsxSheet(
            "Muestra/2026: [total] con un nombre larguísimo", "t", List.of("A"), List.of());
    try (XSSFWorkbook workbook = read(writer.write(List.of(awkward)))) {
      String name = workbook.getSheetName(0);
      assertThat(name).hasSizeLessThanOrEqualTo(31).isNotEmpty();
      assertThat(name).doesNotContain("/", ":", "[", "]");
    }
  }
}
