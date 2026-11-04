package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.AnswerTableResolver.TableCheck;
import co.gov.dane.edi.admin.QuestionFactsService.PackageFacts;
import co.gov.dane.edi.admin.dto.ImportPreview;
import co.gov.dane.edi.admin.dto.PackageManifest;
import co.gov.dane.edi.admin.dto.PackageManifest.TableSum;
import co.gov.dane.edi.admin.dto.PageImpact;
import co.gov.dane.edi.admin.dto.PublicationChecklist;
import co.gov.dane.edi.model.Chapter;
import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.QuestionnaireEntry;
import co.gov.dane.edi.model.SurveyPage;
import co.gov.dane.edi.repository.AnswerColumnRepository;
import co.gov.dane.edi.repository.AnswerRepository;
import co.gov.dane.edi.rules.Condition;
import co.gov.dane.edi.rules.EffectiveRulesService;
import co.gov.dane.edi.rules.QuestionFacts;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RuleSetValidator;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.rules.SkipRule;
import co.gov.dane.edi.service.QuestionnaireService;
import co.gov.dane.edi.service.QuestionnaireService.PageRow;
import co.gov.dane.edi.service.SaveLocks;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Importa en producción un paquete generado en QA. La aplicación solo lee FRM_* aquí: la persona
 * técnica ya ejecutó {@code columnas.sql} y {@code frm.sql}, y la importación comprueba en este
 * orden, cada paso con 422 y la lista de diferencias:
 *
 * <ol>
 *   <li>que el ZIP traiga exactamente las cuatro entradas, sin rutas ni repetidas;
 *   <li>que cada archivo tenga el SHA-256 del manifiesto;
 *   <li>que {@code reglas.json} sea válido, publicado y de la encuesta, año y versión del paquete;
 *   <li>que las filas FRM_* de esta base den las filas y sumas del manifiesto, y
 *   <li>que existan las tablas y columnas de respuesta (estos dos se informan juntos);
 *   <li>que la versión sea posterior a la de este ambiente, o la misma con el mismo contenido.
 * </ol>
 *
 * <p>El ZIP se lee en memoria con un tope de 20 MB descomprimido contado al leer, sin fiarse del
 * tamaño declarado. Lo único que se escribe en disco es el archivo de reglas, al confirmar.
 */
@Service
public class PackageImportService {

  static final int MAX_PACKAGE_BYTES = 5 * 1024 * 1024;
  static final long MAX_UNCOMPRESSED_BYTES = 20L * 1024 * 1024;

  private static final Pattern ENTRY_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
  private static final Pattern CHARACTER_SET = Pattern.compile("[A-Z][A-Z0-9_]{0,29}");

  /** Más entradas que estas no puede ser un paquete: se corta antes de recorrer un ZIP enorme. */
  private static final int MAX_ENTRIES = 16;

  private static final int MAX_DIFFERENCES = 100;
  private static final int BUFFER_BYTES = 8192;

  private static final String IMPORT_OPERATION = "importar-paquete";

  /** Caracteres del SHA-256 del manifiesto que identifican el paquete en la auditoría. */
  private static final int MANIFEST_CODE_LENGTH = 12;

  private final YearCatalogService years;
  private final RulesFileRepository rulesFiles;
  private final FrmSnapshotRepository snapshots;
  private final AnswerTableResolver tables;
  private final AnswerColumnRepository answerColumns;
  private final AnswerRepository answers;
  private final QuestionnaireService questionnaire;
  private final EffectiveRulesService effectiveRules;
  private final QuestionFactsService factsService;
  private final PublicationService publication;
  private final SaveLocks locks;
  private final JsonMapper jsonMapper;

  public PackageImportService(
      YearCatalogService years,
      RulesFileRepository rulesFiles,
      FrmSnapshotRepository snapshots,
      AnswerTableResolver tables,
      AnswerColumnRepository answerColumns,
      AnswerRepository answers,
      QuestionnaireService questionnaire,
      EffectiveRulesService effectiveRules,
      QuestionFactsService factsService,
      PublicationService publication,
      SaveLocks locks,
      JsonMapper jsonMapper) {
    this.years = years;
    this.rulesFiles = rulesFiles;
    this.snapshots = snapshots;
    this.tables = tables;
    this.answerColumns = answerColumns;
    this.answers = answers;
    this.questionnaire = questionnaire;
    this.effectiveRules = effectiveRules;
    this.factsService = factsService;
    this.publication = publication;
    this.locks = locks;
    this.jsonMapper = jsonMapper;
  }

  /**
   * Revisa el paquete y calcula el impacto; con {@code confirm} además reemplaza el archivo de
   * reglas con la versión del paquete. Un paquete ya importado no cambia nada.
   */
  public ImportPreview importPackage(
      String survey, int year, byte[] zip, boolean confirm, String user) {
    years.requireSurveyAndYearRange(survey, year);
    if (zip.length > MAX_PACKAGE_BYTES) {
      throw new PackageTooLargeException();
    }
    Map<String, byte[]> entries = readEntries(zip);
    byte[] manifestBytes = entries.get(PromotionPackageFormat.MANIFEST);
    PackageManifest manifest = parseManifest(manifestBytes);
    requireFilesMatchManifest(manifest, entries);
    RuleSet incoming =
        parseRules(entries.get(PromotionPackageFormat.RULES), manifest, survey, year);
    boolean nonAsciiText =
        FrmScriptWriter.checksCharacterSet(
            new String(entries.get(PromotionPackageFormat.FRM), StandardCharsets.US_ASCII));
    String manifestCode =
        "m" + PromotionPackageFormat.sha256(manifestBytes).substring(0, MANIFEST_CODE_LENGTH);
    return locks.withAdminLock(
        () -> {
          requireSameCharacterSet(manifest, nonAsciiText);
          PackageFacts facts = factsService.packageFacts(survey, year, incoming);
          requireDatabaseMatches(manifest, incoming, facts.facts(), survey, year);
          requireManifestColumns(manifest, incoming, facts.facts());
          requirePublicationGate(incoming, facts);
          Optional<RuleSet> current = rulesFiles.find(survey, year);
          boolean alreadyImported =
              RulesFileRepository.isAlreadyImported(incoming, current.orElse(null));
          List<PageImpact> impact =
              alreadyImported || current.isEmpty()
                  ? List.of()
                  : impact(current.get(), incoming, survey, year);
          boolean overwritesLocalChange =
              !alreadyImported && current.map(PackageImportService::isLocalChange).orElse(false);
          if (confirm) {
            try {
              if (!alreadyImported) {
                rulesFiles.replaceWithPackage(incoming, user, List.of(manifestCode));
              }
            } finally {
              // También con un paquete ya importado: deja las cachés al día con FRM_* y el disco.
              evictCaches();
            }
          }
          return new ImportPreview(
              incoming.version(),
              manifest.kind().jsonValue(),
              impact,
              alreadyImported,
              overwritesLocalChange);
        });
  }

  /** El archivo vigente no lo dejó una importación: es un cambio hecho en este ambiente. */
  private static boolean isLocalChange(RuleSet current) {
    return current.lastChange() == null
        || !IMPORT_OPERATION.equals(current.lastChange().operation());
  }

  /** Paso 1: las cuatro entradas, cada una leída con el tope acumulado de 20 MB. */
  private static Map<String, byte[]> readEntries(byte[] zip) {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    List<String> problems = new ArrayList<>();
    long total = 0;
    int count = 0;
    try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        if (++count > MAX_ENTRIES) {
          throw rejected("El paquete trae demasiadas entradas.", List.of());
        }
        String name = entry.getName();
        boolean keep = false;
        if (entry.isDirectory() || !isSafeName(name)) {
          problems.add("Hay una entrada con un nombre no válido.");
        } else if (!PromotionPackageFormat.ENTRIES.contains(name)) {
          problems.add("Entrada no esperada: " + name);
        } else if (entries.containsKey(name)) {
          problems.add("Entrada repetida: " + name);
        } else {
          keep = true;
        }
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_BYTES];
        int read;
        while ((read = input.read(buffer)) != -1) {
          total += read;
          if (total > MAX_UNCOMPRESSED_BYTES) {
            throw rejected("El paquete supera 20 MB descomprimido.", List.of());
          }
          if (keep) {
            content.write(buffer, 0, read);
          }
        }
        if (keep) {
          entries.put(name, content.toByteArray());
        }
      }
    } catch (ZipException | IllegalArgumentException exception) {
      throw rejected("El archivo no es un ZIP válido.", List.of());
    } catch (IOException exception) {
      throw rejected("El archivo no es un ZIP válido.", List.of());
    }
    for (String expected : PromotionPackageFormat.ENTRIES) {
      if (!entries.containsKey(expected)) {
        problems.add("Falta la entrada: " + expected);
      }
    }
    if (!problems.isEmpty()) {
      throw rejected(
          "El paquete debe traer exactamente manifiesto.json, reglas.json, frm.sql y columnas.sql.",
          problems);
    }
    return entries;
  }

  /** Sin rutas ({@code /}, {@code \}, {@code ..}, unidades): solo nombres planos. */
  private static boolean isSafeName(String name) {
    return name != null && ENTRY_NAME.matcher(name).matches() && !name.contains("..");
  }

  private PackageManifest parseManifest(byte[] content) {
    PackageManifest manifest;
    try {
      manifest = jsonMapper.readValue(content, PackageManifest.class);
    } catch (JacksonException exception) {
      throw rejected(
          "El manifiesto del paquete no es válido.", List.of(PromotionPackageFormat.MANIFEST));
    }
    if (manifest.survey() == null || manifest.kind() == null) {
      throw rejected(
          "El manifiesto del paquete no es válido.", List.of(PromotionPackageFormat.MANIFEST));
    }
    return manifest;
  }

  /** Paso 2: el SHA-256 de cada archivo, sin contar el manifiesto. */
  private static void requireFilesMatchManifest(
      PackageManifest manifest, Map<String, byte[]> entries) {
    List<String> differences = new ArrayList<>();
    for (String name : PromotionPackageFormat.HASHED_ENTRIES) {
      String expected = manifest.files().get(name);
      String actual = PromotionPackageFormat.sha256(entries.get(name));
      if (expected == null || !expected.equalsIgnoreCase(actual)) {
        differences.add(name);
      }
    }
    if (!differences.isEmpty()) {
      throw rejected("Los archivos del paquete no coinciden con el manifiesto.", differences);
    }
  }

  /** Paso 3: reglas válidas, publicadas y del mismo año y versión que el manifiesto y la ruta. */
  private RuleSet parseRules(byte[] content, PackageManifest manifest, String survey, int year) {
    RuleSet rules;
    try {
      rules = jsonMapper.readValue(content, RuleSet.class);
    } catch (JacksonException exception) {
      throw rejected(
          "reglas.json no es un archivo de reglas válido.", List.of(PromotionPackageFormat.RULES));
    }
    List<String> problems = RuleSetValidator.problems(rules);
    if (!problems.isEmpty()) {
      throw rejected("reglas.json no pasa la validación.", capped(problems));
    }
    if (rules.effectiveState() != YearState.PUBLICADO) {
      throw rejected("reglas.json no es de un año publicado.", List.of());
    }
    if (rules.publication() == null || rules.publication().version() > rules.version()) {
      throw rejected("reglas.json no trae una publicación válida.", List.of());
    }
    if (!survey.equals(manifest.survey())
        || !survey.equals(rules.survey())
        || manifest.year() != year
        || rules.year() != year) {
      throw rejected("El paquete es de otra encuesta o año.", List.of());
    }
    if (rules.version() != manifest.version()) {
      throw rejected("La versión de reglas.json no coincide con el manifiesto.", List.of());
    }
    return rules;
  }

  /** Con texto no ASCII, otro juego de caracteres en esta base habría cambiado los textos. */
  private void requireSameCharacterSet(PackageManifest manifest, boolean nonAsciiText) {
    String target = snapshots.characterSet();
    if (!nonAsciiText || target.equals(manifest.characterSet())) {
      return;
    }
    String source =
        manifest.characterSet() != null && CHARACTER_SET.matcher(manifest.characterSet()).matches()
            ? manifest.characterSet()
            : "desconocido";
    String message = "Juego de caracteres distinto: origen " + source + ", destino " + target;
    throw rejected(message, List.of(message));
  }

  /**
   * Pasos 4 y 5, con todas las diferencias: las filas FRM_* de esta base y las tablas y columnas de
   * respuesta que piden las preguntas del año (leídas aquí, no del manifiesto) con el mapeo de
   * {@code reglas.json}.
   */
  private void requireDatabaseMatches(
      PackageManifest manifest,
      RuleSet incoming,
      Map<String, QuestionFacts> facts,
      String survey,
      int year) {
    List<String> differences = new ArrayList<>();
    Map<String, TableSum> actual =
        PromotionPackageFormat.tableSums(snapshots.snapshot(survey, year));
    actual.forEach(
        (table, sum) -> {
          TableSum expected = manifest.tables().get(table);
          if (expected == null) {
            differences.add(table + ": falta en el manifiesto.");
          } else if (expected.rows() != sum.rows()) {
            differences.add(
                table
                    + ": el paquete trae "
                    + expected.rows()
                    + " filas y la base tiene "
                    + sum.rows()
                    + ".");
          } else if (!sum.sum().equalsIgnoreCase(String.valueOf(expected.sum()))) {
            differences.add(table + ": el contenido no coincide.");
          }
        });
    if (!actual.keySet().containsAll(manifest.tables().keySet())) {
      differences.add("El manifiesto trae tablas que no son del paquete.");
    }
    Set<String> validTables = new HashSet<>();
    for (Chapter chapter : Chapter.values()) {
      String table = PromotionPackageFormat.tableOf(incoming, chapter);
      TableCheck check = tables.check(table);
      if (check == TableCheck.VALID) {
        validTables.add(table);
      } else {
        differences.add(tableProblem(check, table, chapter));
      }
    }
    requiredColumns(incoming, facts)
        .forEach(
            (table, columns) -> {
              if (!validTables.contains(table)) {
                return;
              }
              for (String column : columns) {
                if (facts.get(column).columnStatus() != ColumnStatus.ACTIVE) {
                  differences.add("Falta la columna " + table + "." + column + ".");
                }
              }
            });
    if (!differences.isEmpty()) {
      throw rejected("La base de datos no coincide con el paquete.", capped(differences));
    }
  }

  /**
   * El manifiesto debe listar al menos las columnas que el año pide en esta base: uno que liste
   * menos no corresponde a estas preguntas.
   */
  private static void requireManifestColumns(
      PackageManifest manifest, RuleSet incoming, Map<String, QuestionFacts> facts) {
    List<String> missing = new ArrayList<>();
    requiredColumns(incoming, facts)
        .forEach(
            (table, columns) -> {
              List<String> listed = manifest.requiredColumns().get(table);
              for (String column : columns) {
                if (listed == null || !listed.contains(column)) {
                  missing.add(table + "." + column);
                }
              }
            });
    if (!missing.isEmpty()) {
      throw rejected("El manifiesto no coincide con las columnas del año.", capped(missing));
    }
  }

  /** Las columnas de las preguntas del año por la tabla mapeada de su capítulo. */
  private static Map<String, List<String>> requiredColumns(
      RuleSet rules, Map<String, QuestionFacts> facts) {
    Map<String, List<String>> required = new LinkedHashMap<>();
    for (QuestionFacts question : facts.values()) {
      Chapter chapter = SurveyPage.find(question.pageCode()).orElseThrow().chapter();
      required
          .computeIfAbsent(
              PromotionPackageFormat.tableOf(rules, chapter), table -> new ArrayList<>())
          .add(question.column());
    }
    return required;
  }

  /**
   * La lista de publicación sobre esta base: un {@code reglas.json} editado con el hash al día no
   * entra con errores de análisis ni con casos que no pasan (tampoco {@code por_revisar} porque
   * aquí falte una columna).
   */
  private void requirePublicationGate(RuleSet incoming, PackageFacts facts) {
    PublicationChecklist checklist =
        publication.checklistFor(incoming, facts.facts(), facts.legacySkips());
    List<String> failures =
        checklist.items().stream()
            .filter(item -> !item.passed())
            .map(
                item ->
                    item.code()
                        + ": "
                        + item.detail()
                        + (item.elements().isEmpty()
                            ? ""
                            : " (" + String.join(", ", item.elements()) + ")"))
            .toList();
    if (!failures.isEmpty()) {
      throw rejected("reglas.json no pasa la lista de publicación en esta base.", failures);
    }
  }

  /** El nombre solo se repite si cumple el patrón: si no, podría ser texto hostil del paquete. */
  private static String tableProblem(TableCheck check, String table, Chapter chapter) {
    return switch (check) {
      case MISSING -> "Falta la tabla " + table + " del capítulo " + chapter.code() + ".";
      case INCOMPLETE -> "La tabla " + table + " no tiene las columnas C_C, ESTADO y TC.";
      default -> "El nombre de la tabla del capítulo " + chapter.code() + " no es válido.";
    };
  }

  /**
   * Páginas con una columna disparadora o destino de un salto agregado, quitado o cambiado, o con
   * una validación cambiada, y cuántos encuestados las guardaron. La tabla y la columna salen del
   * resolvedor y del cuestionario de esta base, nunca del paquete.
   */
  private List<PageImpact> impact(RuleSet current, RuleSet incoming, String survey, int year) {
    Set<String> changed = changedColumns(current, incoming);
    if (changed.isEmpty()) {
      return List.of();
    }
    List<PageImpact> impact = new ArrayList<>();
    for (SurveyPage page : SurveyPage.ordered()) {
      List<QuestionnaireEntry> entries =
          questionnaire.pageRows(page, survey, year).stream().map(PageRow::entry).toList();
      boolean affected =
          entries.stream()
              .anyMatch(
                  entry -> entry.answer() != null && changed.contains(entry.answer().columnCode()));
      if (!affected) {
        continue;
      }
      List<String> active = QuestionnaireService.activeItemColumns(entries);
      long saved =
          active.isEmpty() ? 0 : answers.countAnswered(page.chapter(), survey, year, active.get(0));
      impact.add(new PageImpact(page.code(), saved));
    }
    return impact;
  }

  private static Set<String> changedColumns(RuleSet current, RuleSet incoming) {
    Set<String> columns = new LinkedHashSet<>();
    Map<String, SkipRule> before = byId(current.skips());
    Map<String, SkipRule> after = byId(incoming.skips());
    Set<String> ids = new LinkedHashSet<>(before.keySet());
    ids.addAll(after.keySet());
    for (String id : ids) {
      SkipRule old = before.get(id);
      SkipRule updated = after.get(id);
      if (!Objects.equals(old, updated)) {
        addColumns(columns, old);
        addColumns(columns, updated);
      }
    }
    Set<String> validated = new LinkedHashSet<>(current.validations().keySet());
    validated.addAll(incoming.validations().keySet());
    for (String column : validated) {
      if (!Objects.equals(current.validations().get(column), incoming.validations().get(column))) {
        columns.add(column);
      }
    }
    return columns;
  }

  private static Map<String, SkipRule> byId(List<SkipRule> skips) {
    Map<String, SkipRule> byId = new LinkedHashMap<>();
    skips.forEach(skip -> byId.putIfAbsent(skip.id(), skip));
    return byId;
  }

  private static void addColumns(Set<String> columns, SkipRule skip) {
    if (skip == null) {
      return;
    }
    addColumns(columns, skip.when());
    if (skip.then() != null && skip.then().questions() != null) {
      columns.addAll(skip.then().questions());
    }
  }

  private static void addColumns(Set<String> columns, Condition condition) {
    if (condition == null) {
      return;
    }
    if (condition.question() != null) {
      columns.add(condition.question());
    }
    if (condition.all() != null) {
      columns.addAll(condition.all());
    }
    if (condition.any() != null) {
      columns.addAll(condition.any());
    }
    if (condition.and() != null) {
      condition.and().forEach(nested -> addColumns(columns, nested));
    }
    if (condition.or() != null) {
      condition.or().forEach(nested -> addColumns(columns, nested));
    }
  }

  /** Las preguntas, las reglas, las tablas y las columnas pudieron cambiar con el paquete. */
  private void evictCaches() {
    questionnaire.evict();
    effectiveRules.evict();
    tables.evict();
    answerColumns.evictAll();
  }

  private static List<String> capped(List<String> differences) {
    return differences.size() > MAX_DIFFERENCES
        ? List.copyOf(differences.subList(0, MAX_DIFFERENCES))
        : differences;
  }

  private static PackageRejectedException rejected(String detail, List<String> differences) {
    return new PackageRejectedException(detail, differences);
  }
}
