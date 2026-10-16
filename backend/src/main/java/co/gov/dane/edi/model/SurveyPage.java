package co.gov.dane.edi.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Página del cuestionario en el orden del menú heredado (D16: se guardan en este orden). */
public record SurveyPage(String code, Chapter chapter) {

  private static final List<SurveyPage> ORDERED =
      Arrays.stream(Chapter.values())
          .flatMap(
              chapter -> chapter.pageCodes().stream().map(code -> new SurveyPage(code, chapter)))
          .toList();

  public static List<SurveyPage> ordered() {
    return ORDERED;
  }

  public static Optional<SurveyPage> find(String code) {
    if (code == null) {
      return Optional.empty();
    }
    String normalized = code.strip().toUpperCase(Locale.ROOT);
    return ORDERED.stream().filter(page -> page.code().equals(normalized)).findFirst();
  }

  public Optional<SurveyPage> next() {
    int index = ORDERED.indexOf(this);
    return index + 1 < ORDERED.size() ? Optional.of(ORDERED.get(index + 1)) : Optional.empty();
  }

  /** La primera página de un capítulo crea la fila del encuestado; las demás la actualizan. */
  public boolean isFirstOfChapter() {
    return chapter.pageCodes().get(0).equals(code);
  }
}
