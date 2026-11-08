package co.gov.dane.edi.operation.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Escritor común de {@code .xlsx} de los reportes. Los textos siempre se escriben como celda de
 * texto, nunca como fórmula, porque vienen de la base (entidades, nombres) y una celda que empiece
 * por {@code =}, {@code +}, {@code -} o {@code @} no debe ejecutarse al abrir el archivo.
 */
@Component
public class XlsxWriter {

  private static final int ROWS_IN_MEMORY = 200;
  private static final int MAX_SHEET_NAME = 31;
  private static final int MAX_COLUMN_WIDTH_CHARS = 60;
  private static final int MIN_COLUMN_WIDTH_CHARS = 10;
  private static final int CHARS_TO_WIDTH_UNITS = 256;
  private static final int FROZEN_ROWS = 2;
  private static final String DECIMAL_FORMAT = "0.00";
  private static final String DATE_FORMAT = "yyyy-mm-dd";
  private static final String DATE_TIME_FORMAT = "yyyy-mm-dd hh:mm";
  private static final String FORBIDDEN_NAME_CHARS = "[\\\\/?*\\[\\]:]";

  public byte[] write(List<XlsxSheet> sheets) {
    SXSSFWorkbook workbook = new SXSSFWorkbook(ROWS_IN_MEMORY);
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      Styles styles = new Styles(workbook);
      for (int index = 0; index < sheets.size(); index++) {
        writeSheet(workbook, styles, sheets.get(index), index + 1);
      }
      workbook.write(output);
      return output.toByteArray();
    } catch (IOException exception) {
      throw new UncheckedIOException("No se pudo generar el archivo de Excel.", exception);
    } finally {
      workbook.dispose();
      closeQuietly(workbook);
    }
  }

  private static void closeQuietly(SXSSFWorkbook workbook) {
    try {
      workbook.close();
    } catch (IOException ignored) {
      // Solo libera recursos temporales.
    }
  }

  private void writeSheet(SXSSFWorkbook workbook, Styles styles, XlsxSheet sheet, int position) {
    SXSSFSheet target = workbook.createSheet(safeName(sheet.name(), position));
    int columns = sheet.headers().size();
    Cell titleCell = target.createRow(0).createCell(0);
    titleCell.setCellValue(sheet.title() == null ? "" : sheet.title());
    titleCell.setCellStyle(styles.title);
    Row headerRow = target.createRow(1);
    for (int column = 0; column < columns; column++) {
      Cell cell = headerRow.createCell(column);
      cell.setCellValue(sheet.headers().get(column));
      cell.setCellStyle(styles.header);
    }
    int rowIndex = FROZEN_ROWS;
    for (List<Object> values : sheet.rows()) {
      Row row = target.createRow(rowIndex++);
      for (int column = 0; column < values.size(); column++) {
        writeCell(row, column, values.get(column), styles);
      }
    }
    // Título y encabezado quedan visibles al desplazarse.
    target.createFreezePane(0, FROZEN_ROWS);
    for (int column = 0; column < columns; column++) {
      target.setColumnWidth(column, widthFor(sheet.headers().get(column)));
    }
  }

  private static int widthFor(String header) {
    int length = header == null ? 0 : header.length() + 2;
    int chars = Math.min(MAX_COLUMN_WIDTH_CHARS, Math.max(MIN_COLUMN_WIDTH_CHARS, length));
    return chars * CHARS_TO_WIDTH_UNITS;
  }

  private static void writeCell(Row row, int column, Object value, Styles styles) {
    if (value == null) {
      return;
    }
    Cell cell = row.createCell(column);
    if (value instanceof BigDecimal decimal) {
      cell.setCellValue(decimal.doubleValue());
      cell.setCellStyle(styles.decimal);
    } else if (value instanceof Number number) {
      cell.setCellValue(number.doubleValue());
    } else if (value instanceof LocalDateTime dateTime) {
      cell.setCellValue(dateTime);
      cell.setCellStyle(styles.dateTime);
    } else if (value instanceof LocalDate date) {
      cell.setCellValue(date);
      cell.setCellStyle(styles.date);
    } else if (value instanceof Boolean flag) {
      cell.setCellValue(flag);
    } else {
      // setCellValue(String) crea una celda de texto: nunca se interpreta como fórmula.
      cell.setCellValue(value.toString());
    }
  }

  private static String safeName(String requested, int position) {
    String cleaned =
        requested == null ? "" : requested.replaceAll(FORBIDDEN_NAME_CHARS, " ").strip();
    if (cleaned.isEmpty()) {
      cleaned = "Hoja " + position;
    }
    return cleaned.length() > MAX_SHEET_NAME
        ? cleaned.substring(0, MAX_SHEET_NAME).strip()
        : cleaned;
  }

  private static final class Styles {
    private final CellStyle title;
    private final CellStyle header;
    private final CellStyle decimal;
    private final CellStyle date;
    private final CellStyle dateTime;

    private Styles(SXSSFWorkbook workbook) {
      CreationHelper helper = workbook.getCreationHelper();
      Font bold = workbook.createFont();
      bold.setBold(true);
      title = workbook.createCellStyle();
      title.setFont(bold);
      header = workbook.createCellStyle();
      header.setFont(bold);
      header.setWrapText(true);
      decimal = workbook.createCellStyle();
      decimal.setDataFormat(helper.createDataFormat().getFormat(DECIMAL_FORMAT));
      date = workbook.createCellStyle();
      date.setDataFormat(helper.createDataFormat().getFormat(DATE_FORMAT));
      dateTime = workbook.createCellStyle();
      dateTime.setDataFormat(helper.createDataFormat().getFormat(DATE_TIME_FORMAT));
    }
  }
}
