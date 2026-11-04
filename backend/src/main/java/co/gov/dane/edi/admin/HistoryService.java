package co.gov.dane.edi.admin;

import co.gov.dane.edi.admin.dto.HistoryView;
import co.gov.dane.edi.rules.RuleSet;
import co.gov.dane.edi.rules.RulesFileRepository;
import co.gov.dane.edi.service.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Lectura del historial de versiones del archivo de reglas de un año (también si está publicado).
 */
@Service
public class HistoryService {

  private final YearCatalogService years;
  private final RulesFileRepository rulesFiles;

  public HistoryService(YearCatalogService years, RulesFileRepository rulesFiles) {
    this.years = years;
    this.rulesFiles = rulesFiles;
  }

  public HistoryView view(String survey, int year) {
    RuleSet current = years.requireYear(survey, year);
    return new HistoryView(
        current.version(), current.effectiveState(), rulesFiles.history(survey, year));
  }

  /** Reglas tal como estaban guardadas en esa versión; 404 si no existe. */
  public RuleSet version(String survey, int year, int version) {
    years.requireYear(survey, year);
    return rulesFiles
        .historyVersion(survey, year, version)
        .orElseThrow(() -> new ResourceNotFoundException(RulesAdminService.MISSING_VERSION));
  }
}
