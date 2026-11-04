package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.dto.YearSummary;
import co.gov.dane.edi.config.SurveyProperties;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.service.error.ConflictException;
import co.gov.dane.edi.service.error.InvalidEditException;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import co.gov.dane.edi.service.error.SurveyNotAvailableException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/** Catálogo de años de cada encuesta, según sus archivos de reglas (no hay tabla de años). */
@Service
public class YearCatalogService {

  private static final Set<String> SURVEYS = Set.of("EDIN", "EDID");
  private static final int MIN_YEAR = 2020;
  private static final int MAX_YEAR = 2100;
  private static final int MIN_REASON_LENGTH = 10;
  private static final int MAX_REASON_LENGTH = 500;

  private final RulesFileRepository rulesFiles;
  private final SurveyProperties survey;

  public YearCatalogService(RulesFileRepository rulesFiles, SurveyProperties survey) {
    this.rulesFiles = rulesFiles;
    this.survey = survey;
  }

  /** Lanza 404 si la encuesta no es EDIN ni EDID. */
  public void requireSurvey(String surveyCode) {
    if (!SURVEYS.contains(surveyCode)) {
      throw new ResourceNotFoundException("La encuesta solicitada no existe.");
    }
  }

  public List<YearSummary> years(String surveyCode) {
    requireSurvey(surveyCode);
    Set<Integer> years = new TreeSet<>(rulesFiles.years(surveyCode));
    if (surveyCode.equals(survey.code())) {
      years.add(survey.year());
    }
    return years.stream()
        .map(year -> summary(surveyCode, year, rulesFiles.load(surveyCode, year)))
        .toList();
  }

  /** Reglas del año; 404 si no tiene archivo (salvo el activo, que se siembra). */
  public RuleSet requireYear(String surveyCode, int year) {
    requireSurvey(surveyCode);
    if (year < MIN_YEAR || year > MAX_YEAR) {
      throw new ResourceNotFoundException("El año solicitado no existe.");
    }
    if (isActive(surveyCode, year)) {
      return rulesFiles.load(surveyCode, year);
    }
    return rulesFiles
        .find(surveyCode, year)
        .orElseThrow(() -> new ResourceNotFoundException("El año solicitado no existe."));
  }

  /** A lo sumo hay un borrador por encuesta. */
  public Optional<Integer> draftYear(String surveyCode) {
    return years(surveyCode).stream()
        .filter(summary -> summary.state() == YearState.BORRADOR)
        .map(YearSummary::year)
        .findFirst();
  }

  public void requireDraft(String surveyCode, int year) {
    RuleSet rules = requireYear(surveyCode, year);
    if (rules.effectiveState() != YearState.BORRADOR) {
      throw new ConflictException(
          "El cuestionario de " + year + " está publicado y no se puede editar.");
    }
  }

  /**
   * Comprueba que el año admita un cambio de esa clase y devuelve el motivo normalizado. En un
   * borrador cualquier clase pasa, el motivo se ignora y el resultado es nulo. En un año publicado
   * solo pasan las correcciones y exigen un motivo de 10 a 500 caracteres (422); el cambio
   * estructural recibe 409 aunque llegue con motivo.
   */
  public String requireEditable(String surveyCode, int year, EditKind kind, String reason) {
    RuleSet rules = requireYear(surveyCode, year);
    if (rules.effectiveState() == YearState.BORRADOR) {
      return null;
    }
    if (kind == EditKind.STRUCTURAL) {
      throw new ConflictException(
          "El cuestionario de "
              + year
              + " está publicado: solo se permiten correcciones de textos, saltos y"
              + " validaciones.");
    }
    String trimmed = reason == null ? "" : reason.strip();
    if (trimmed.length() < MIN_REASON_LENGTH || trimmed.length() > MAX_REASON_LENGTH) {
      throw new InvalidEditException(
          "motivo", "Indique el motivo de la corrección (entre 10 y 500 caracteres).");
    }
    return trimmed;
  }

  /** Puerta del encuestado: el año activo en borrador no se sirve. */
  public void requireActiveYearAvailable() {
    if (rulesFiles.load(survey.code(), survey.year()).effectiveState() == YearState.BORRADOR) {
      throw new SurveyNotAvailableException();
    }
  }

  private boolean isActive(String surveyCode, int year) {
    return surveyCode.equals(survey.code()) && year == survey.year();
  }

  private YearSummary summary(String surveyCode, int year, RuleSet rules) {
    return new YearSummary(
        year, rules.effectiveState(), rules.version(), isActive(surveyCode, year));
  }
}
