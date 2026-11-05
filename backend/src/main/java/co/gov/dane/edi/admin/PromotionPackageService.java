package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.ColumnScriptService.ColumnAddition;
import co.gov.dane.edi.admin.ColumnScriptService.ManualColumn;
import co.gov.dane.edi.admin.ColumnScriptService.TableCopy;
import co.gov.dane.edi.admin.dto.PackageManifest;
import co.gov.dane.edi.admin.dto.PublicationChecklist;
import co.gov.dane.edi.admin.dto.YearSummary;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.NewColumn;
import co.gov.dane.edi.rules.QuestionFacts;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.service.SaveLocks;
import co.gov.dane.edi.service.error.ConflictException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Arma en QA el paquete de promoción de un año publicado: {@code manifiesto.json}, {@code
 * reglas.json}, {@code frm.sql} y {@code columnas.sql}, en ese orden y en memoria. Todo se lee bajo
 * el candado de administración, así la fotografía FRM_*, las reglas y el manifiesto corresponden al
 * mismo momento aunque otra persona esté corrigiendo.
 */
@Service
public class PromotionPackageService {

  static final String NOT_PUBLISHED = "Solo se generan paquetes de un año publicado.";
  static final String NO_CORRECTIONS = "No hay correcciones posteriores a la publicación.";
  static final String CHECKLIST_FAILS =
      "El año no pasa la lista de publicación: corríjalo antes de generar el paquete.";
  private static final String OPERATION = "generar-paquete";

  /** El ZIP y el nombre con que se descarga: {@code <ENC>-<AAAA>-v<N>-<tipo>.zip}. */
  public record PackageFile(String fileName, byte[] content) {}

  private final YearCatalogService years;
  private final FrmSnapshotRepository snapshots;
  private final FrmScriptWriter frmScripts;
  private final ColumnScriptService columnScripts;
  private final QuestionFactsService factsService;
  private final AnswerTableResolver tables;
  private final PublicationService publication;
  private final EffectiveRulesService effectiveRules;
  private final AdminAudit audit;
  private final SaveLocks locks;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  public PromotionPackageService(
      YearCatalogService years,
      FrmSnapshotRepository snapshots,
      FrmScriptWriter frmScripts,
      ColumnScriptService columnScripts,
      QuestionFactsService factsService,
      AnswerTableResolver tables,
      PublicationService publication,
      EffectiveRulesService effectiveRules,
      AdminAudit audit,
      SaveLocks locks,
      JsonMapper jsonMapper,
      Clock clock) {
    this.years = years;
    this.snapshots = snapshots;
    this.frmScripts = frmScripts;
    this.columnScripts = columnScripts;
    this.factsService = factsService;
    this.tables = tables;
    this.publication = publication;
    this.effectiveRules = effectiveRules;
    this.audit = audit;
    this.locks = locks;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  /**
   * El paquete del año. Uno de corrección exige una versión posterior a la publicación: si no, no
   * hay nada que corregir en producción.
   */
  public PackageFile build(String survey, int year, PackageKind kind, String user) {
    return locks.withAdminLock(
        () -> {
          RuleSet rules = years.requireYear(survey, year);
          if (rules.effectiveState() != YearState.PUBLICADO) {
            throw new ConflictException(NOT_PUBLISHED);
          }
          if (kind == PackageKind.CORRECCION
              && (rules.publication() == null
                  || rules.version() <= rules.publication().version())) {
            throw new ConflictException(NO_CORRECTIONS);
          }
          Map<String, QuestionFacts> facts = factsService.facts(survey, year);
          // Producción corre la misma lista al importar: un paquete que no la pasa en QA no sirve.
          PublicationChecklist checklist =
              publication.checklistFor(rules, facts, effectiveRules.legacySkips(survey, year));
          if (!checklist.ready()) {
            throw new PublicationBlockedException(CHECKLIST_FAILS, checklist);
          }
          FrmSnapshot snapshot = snapshots.snapshot(survey, year);
          String characterSet = snapshots.characterSet();

          Map<String, byte[]> files = new LinkedHashMap<>();
          files.put(PromotionPackageFormat.RULES, PromotionPackageFormat.json(jsonMapper, rules));
          files.put(
              PromotionPackageFormat.FRM, ascii(frmScripts.write(snapshot, kind, characterSet)));
          files.put(
              PromotionPackageFormat.COLUMNS, ascii(columnScript(survey, year, rules, facts)));
          Map<String, String> sums = new LinkedHashMap<>();
          files.forEach((name, content) -> sums.put(name, PromotionPackageFormat.sha256(content)));
          Instant generatedAt = clock.instant();
          PackageManifest manifest =
              new PackageManifest(
                  survey,
                  year,
                  rules.version(),
                  kind,
                  generatedAt,
                  user,
                  characterSet,
                  PromotionPackageFormat.tableSums(snapshot),
                  requiredColumns(rules, facts),
                  sums);

          Map<String, byte[]> entries = new LinkedHashMap<>();
          entries.put(
              PromotionPackageFormat.MANIFEST, PromotionPackageFormat.json(jsonMapper, manifest));
          entries.putAll(files);
          byte[] zip = zip(entries, generatedAt);
          audit.record(
              user, survey, year, OPERATION, List.of(kind.jsonValue(), "v" + rules.version()));
          return new PackageFile(
              survey + "-" + year + "-v" + rules.version() + "-" + kind.jsonValue() + ".zip", zip);
        });
  }

  /** Las columnas de las preguntas del año agrupadas por la tabla mapeada de su capítulo. */
  private static Map<String, List<String>> requiredColumns(
      RuleSet rules, Map<String, QuestionFacts> facts) {
    Map<String, List<String>> required = new LinkedHashMap<>();
    for (QuestionFacts question : facts.values()) {
      required
          .computeIfAbsent(tableOf(rules, question), table -> new ArrayList<>())
          .add(question.column());
    }
    return required;
  }

  /**
   * Las tablas mapeadas que el año estrena, copiadas vacías de la del año anterior, y todas las
   * columnas requeridas en la tabla de su capítulo: con la definición de {@code columnasNuevas} o,
   * si no está ahí, con la del diccionario de QA. En QA ya existen; el script solo las crea donde
   * falten.
   */
  private String columnScript(
      String survey, int year, RuleSet rules, Map<String, QuestionFacts> facts) {
    Optional<Integer> previous =
        years.years(survey).stream()
            .map(YearSummary::year)
            .filter(candidate -> candidate < year)
            .max(Integer::compare);
    List<TableCopy> copies = new ArrayList<>();
    for (Chapter chapter : Chapter.values()) {
      String mapped = rules.tables().get(chapter.code());
      if (mapped == null || previous.isEmpty()) {
        continue;
      }
      String source = tables.tableFor(chapter, survey, previous.get());
      if (!source.equals(mapped)) {
        copies.add(new TableCopy(mapped, source));
      }
    }
    List<ColumnAddition> additions = new ArrayList<>();
    List<ManualColumn> manual = new ArrayList<>();
    for (QuestionFacts question : facts.values()) {
      String table = tableOf(rules, question);
      NewColumn definition = rules.newColumns().get(question.column());
      Optional<ColumnAddition> addition =
          definition != null
              ? Optional.of(
                  new ColumnAddition(
                      table, question.column(), definition.type(), definition.length()))
              : ColumnScriptService.additionFrom(table, question.capacity());
      addition.ifPresentOrElse(
          additions::add, () -> manual.add(new ManualColumn(table, question.column())));
    }
    return columnScripts.packageScript(survey, year, copies, additions, manual);
  }

  private static String tableOf(RuleSet rules, QuestionFacts question) {
    Chapter chapter = SurveyPage.find(question.pageCode()).orElseThrow().chapter();
    return PromotionPackageFormat.tableOf(rules, chapter);
  }

  /** Los dos scripts ya son ASCII puro: el escritor y los identificadores validados lo aseguran. */
  private static byte[] ascii(String script) {
    return script.getBytes(StandardCharsets.US_ASCII);
  }

  private static byte[] zip(Map<String, byte[]> entries, Instant generatedAt) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream output = new ZipOutputStream(bytes)) {
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        ZipEntry zipEntry = new ZipEntry(entry.getKey());
        zipEntry.setTime(generatedAt.toEpochMilli());
        output.putNextEntry(zipEntry);
        output.write(entry.getValue());
        output.closeEntry();
      }
    } catch (IOException exception) {
      throw new UncheckedIOException("No fue posible armar el paquete", exception);
    }
    return bytes.toByteArray();
  }
}
