package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.dane.edi.admin.ColumnScriptService.ColumnAddition;
import co.gov.dane.edi.admin.ColumnScriptService.TableCopy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ColumnScriptServiceTest {

  private final ColumnScriptService scripts = new ColumnScriptService();

  @Test
  void generatesTheExactAlterTableForVarchar() {
    assertThat(scripts.alterTable("EDIN_20_CAP_C_EXP_PERS", "C30A", "VARCHAR2", 2))
        .isEqualTo("ALTER TABLE EDIN_20_CAP_C_EXP_PERS ADD (C30A VARCHAR2(2));");
  }

  @Test
  void generatesTheAlterTableForNumberWithPrecision() {
    assertThat(scripts.alterTable("EDIN_20_CAP_D_GEST_TAL", "D31", "number", 5))
        .isEqualTo("ALTER TABLE EDIN_20_CAP_D_GEST_TAL ADD (D31 NUMBER(5));");
  }

  @Test
  void generatesAnEmptyCopyOfATable() {
    assertThat(scripts.createTableLike("EDIN_21_CAP_B_UBICA", "EDIN_20_CAP_B_UBICA"))
        .isEqualTo(
            "CREATE TABLE EDIN_21_CAP_B_UBICA AS SELECT * FROM EDIN_20_CAP_B_UBICA WHERE 1 = 0;");
  }

  @ParameterizedTest
  @ValueSource(strings = {"C30A; DROP", "c30a", "C", "1C30", "C30A ", "C30-A", "DUAL'--"})
  void rejectsUnsafeColumnNames(String column) {
    assertThatThrownBy(() -> scripts.alterTable("EDIN_20_CAP_C_EXP_PERS", column, "VARCHAR2", 2))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"X; DROP TABLE Y", "edin", "T", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "T B", ""})
  void rejectsUnsafeTableNames(String table) {
    assertThatThrownBy(() -> scripts.alterTable(table, "C30A", "VARCHAR2", 2))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> scripts.createTableLike(table, "EDIN_20_CAP_B_UBICA"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> scripts.createTableLike("EDIN_21_CAP_B_UBICA", table))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsIncoherentTypeAndLength() {
    assertThatThrownBy(() -> scripts.alterTable("EDIN_20_CAP_B_UBICA", "B30", "VARCHAR2", 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> scripts.alterTable("EDIN_20_CAP_B_UBICA", "B30", "VARCHAR2", 4001))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> scripts.alterTable("EDIN_20_CAP_B_UBICA", "B30", "NUMBER", 39))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> scripts.alterTable("EDIN_20_CAP_B_UBICA", "B30", "NUMBER", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> scripts.alterTable("EDIN_20_CAP_B_UBICA", "B30", "CLOB", 5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> scripts.alterTable("EDIN_20_CAP_B_UBICA", "B30", null, 5))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void packageScriptCreatesOnlyWhatIsMissingAndIsAscii() {
    String script =
        scripts.packageScript(
            "EDIN",
            2027,
            List.of(new TableCopy("EDIN_27_CAP_C", "EDIN_20_CAP_C_EXP_PERS")),
            List.of(new ColumnAddition("EDIN_27_CAP_C", "C30A", "varchar2", 2)));

    assertThat(script)
        .startsWith(
            "SET DEFINE OFF\nWHENEVER SQLERROR EXIT FAILURE ROLLBACK\n"
                + "WHENEVER OSERROR EXIT FAILURE ROLLBACK\n")
        .doesNotContain("AUTOCOMMIT")
        .contains("-- Encuesta: EDIN\n-- Anio: 2027\n")
        .contains("no se puede deshacer")
        .contains("ANTES de frm.sql")
        .contains(
            "FROM ALL_TABLES WHERE OWNER = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')"
                + " AND TABLE_NAME = 'EDIN_27_CAP_C';")
        .contains(
            "EXECUTE IMMEDIATE 'CREATE TABLE EDIN_27_CAP_C AS SELECT * FROM"
                + " EDIN_20_CAP_C_EXP_PERS WHERE 1 = 0';")
        .contains("AND TABLE_NAME = 'EDIN_27_CAP_C' AND COLUMN_NAME = 'C30A';")
        .contains("EXECUTE IMMEDIATE 'ALTER TABLE EDIN_27_CAP_C ADD (C30A VARCHAR2(2))';");
    // La tabla va antes que sus columnas nuevas.
    assertThat(script.indexOf("CREATE TABLE")).isLessThan(script.indexOf("ALTER TABLE"));
    assertThat(script.chars().allMatch(character -> character < 0x80)).isTrue();
    assertThat(script.lines().filter("/"::equals).count()).isEqualTo(2);
  }

  @Test
  void packageScriptWithNothingToCreateIsOnlyAComment() {
    String script = scripts.packageScript("EDIN", 2027, List.of(), List.of());

    assertThat(script).isNotBlank();
    assertThat(script.lines()).allMatch(line -> line.startsWith("--"));
  }

  @Test
  void packageScriptRejectsUnsafeNames() {
    List<TableCopy> noTables = List.of();
    List<ColumnAddition> badColumn =
        List.of(new ColumnAddition("EDIN_27_CAP_C", "C30A'", "VARCHAR2", 2));
    List<TableCopy> badSource = List.of(new TableCopy("EDIN_27_CAP_C", "X'; DROP"));
    List<ColumnAddition> noColumns = List.of();

    assertThatThrownBy(() -> scripts.packageScript("EDIN", 2027, noTables, badColumn))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> scripts.packageScript("EDIN", 2027, badSource, noColumns))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> scripts.packageScript("EDIN\n--", 2027, noTables, noColumns))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsTheLimits() {
    assertThat(scripts.alterTable("EDIN_20_CAP_B_UBICA", "B30", "VARCHAR2", 4000))
        .isEqualTo("ALTER TABLE EDIN_20_CAP_B_UBICA ADD (B30 VARCHAR2(4000));");
    assertThat(scripts.alterTable("EDIN_20_CAP_B_UBICA", "B30", "NUMBER", 38))
        .isEqualTo("ALTER TABLE EDIN_20_CAP_B_UBICA ADD (B30 NUMBER(38));");
  }
}
