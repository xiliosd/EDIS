package co.gov.dane.edi.operation.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.dane.edi.operation.OperationProperties;
import co.gov.dane.edi.operation.report.dto.CoverageReport;
import co.gov.dane.edi.operation.report.dto.CutSummary;
import co.gov.dane.edi.operation.report.dto.DepartmentCoverage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** Almacén de cortes en disco: ida y vuelta, orden, filtro y ruta segura. */
class CoverageCutStoreTest {

  private static final ZoneOffset BOGOTA = ZoneOffset.ofHours(-5);

  @TempDir Path directory;

  private CoverageCutStore store;

  @BeforeEach
  void createStore() {
    store =
        new CoverageCutStore(
            new OperationProperties(directory.toString(), 8), JsonMapper.builder().build());
  }

  @Test
  void savedCutIsReadBackWithItsReport() {
    CoverageCut cut =
        cut("20261107-101500-a1b2c3", 5, OffsetDateTime.of(2026, 11, 7, 10, 15, 0, 0, BOGOTA));

    store.save(cut);

    CoverageCut read = store.find("20261107-101500-a1b2c3").orElseThrow();
    assertThat(read.id()).isEqualTo(cut.id());
    assertThat(read.departmentCode()).isEqualTo(5);
    assertThat(read.generatedBy()).isEqualTo("territorial-multi");
    assertThat(read.report().departments()).hasSize(1);
    assertThat(read.report().departments().get(0).name()).isEqualTo("Antioquia");
    assertThat(read.generatedAt().toInstant()).isEqualTo(cut.generatedAt().toInstant());
    assertThat(directory.resolve("2026").resolve("20261107-101500-a1b2c3.json")).isRegularFile();
  }

  @Test
  void twoCutsOnTheSameDayAreBothKeptNewestFirst() {
    store.save(
        cut("20261107-101500-aaaaaa", 5, OffsetDateTime.of(2026, 11, 7, 10, 15, 0, 0, BOGOTA)));
    store.save(
        cut("20261107-153000-bbbbbb", 5, OffsetDateTime.of(2026, 11, 7, 15, 30, 0, 0, BOGOTA)));

    List<CutSummary> summaries = store.list(2026, null);

    assertThat(summaries)
        .extracting(CutSummary::id)
        .containsExactly("20261107-153000-bbbbbb", "20261107-101500-aaaaaa");
  }

  @Test
  void listIsFilteredByDepartmentsAndYear() {
    store.save(
        cut("20261107-101500-aaaaaa", 5, OffsetDateTime.of(2026, 11, 7, 10, 15, 0, 0, BOGOTA)));
    store.save(
        cut("20261107-111500-bbbbbb", 8, OffsetDateTime.of(2026, 11, 7, 11, 15, 0, 0, BOGOTA)));
    store.save(
        cut("20261107-121500-cccccc", 76, OffsetDateTime.of(2026, 11, 7, 12, 15, 0, 0, BOGOTA)));

    assertThat(store.list(2026, List.of(5, 8)))
        .extracting(CutSummary::departmentCode)
        .containsExactlyInAnyOrder(5, 8);
    assertThat(store.list(2026, List.of())).isEmpty();
    assertThat(store.list(2026, null)).hasSize(3);
    assertThat(store.list(2025, null)).isEmpty();
  }

  @Test
  void invalidIdsNeverTouchTheDisk() throws IOException {
    assertThat(store.find("../x")).isEqualTo(Optional.empty());
    assertThat(store.find("..\\..\\x")).isEmpty();
    assertThat(store.find("20261107-101500-ABCDEF")).isEmpty();
    assertThat(store.find(null)).isEmpty();
    // La raíz ni siquiera se creó: ninguna búsqueda inválida llegó al disco.
    try (Stream<Path> entries = Files.list(directory)) {
      assertThat(entries).isEmpty();
    }
    assertThatThrownBy(() -> store.save(cut("../x", 5, OffsetDateTime.now(BOGOTA))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unknownValidIdIsEmpty() {
    assertThat(store.find("20261107-101500-a1b2c3")).isEmpty();
  }

  private static CoverageCut cut(String id, int departmentCode, OffsetDateTime generatedAt) {
    CoverageReport report =
        new CoverageReport(
            ReportSurvey.GENERAL,
            2026,
            generatedAt,
            List.of(new DepartmentCoverage(departmentCode, "Antioquia", List.of(), null)),
            1,
            0,
            false,
            null,
            List.of());
    return new CoverageCut(
        id, ReportSurvey.GENERAL, 2026, departmentCode, "territorial-multi", generatedAt, report);
  }
}
