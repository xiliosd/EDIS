package co.gov.dane.edi.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Compuerta única del modo de solo lectura del cuestionario. Con la escritura apagada rechaza toda
 * operación de la API de administración que no sea de lectura (POST, PUT, PATCH, DELETE) antes de
 * resolver el cuerpo, tomar el candado o tocar la base o el archivo de reglas. Cubre toda
 * /api/v1/admin, no solo el cuestionario: una escritura nueva de administración queda negada hasta
 * que alguien decida, de forma explícita, que puede ocurrir en producción.
 *
 * <p>Bloquea por defecto: una operación de escritura nueva queda cubierta sin hacer nada, y para
 * permitirla en producción hay que marcarla con {@link AllowedInReadOnlyQuestionnaire}.
 */
public class QuestionnaireWriteGuard implements HandlerInterceptor {

  /** Rutas que cubre la compuerta; también decide dónde el contrato documenta el 403. */
  public static final String[] PATH_PATTERNS = {"/api/v1/admin/**"};

  private static final List<PathPattern> PARSED_PATTERNS =
      Arrays.stream(PATH_PATTERNS).map(PathPatternParser.defaultInstance::parse).toList();

  private static final Logger LOGGER = LoggerFactory.getLogger(QuestionnaireWriteGuard.class);

  private static final Set<RequestMethod> READ_METHODS =
      Set.of(RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS);

  private final boolean writesEnabled;

  public QuestionnaireWriteGuard(QuestionnaireWritesProperties properties) {
    this.writesEnabled = properties.questionnaireWritesEnabled();
    if (!writesEnabled) {
      LOGGER.info(
          "Cuestionario en solo lectura: la administración no escribe FRM_* ni el archivo de"
              + " reglas; solo se importan paquetes de promoción y se permiten las escrituras"
              + " operativas (cortes de cobertura, novedades y claves).");
    }
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!writesEnabled && handler instanceof HandlerMethod method && isWrite(method)) {
      throw new QuestionnaireReadOnlyException();
    }
    return true;
  }

  /**
   * Si alguno de los patrones de un mapeo (p. ej. "/api/v1/admin/years/{encuesta}") queda cubierto.
   */
  public static boolean covers(Collection<String> mappingPatterns) {
    return mappingPatterns.stream()
        .map(PathContainer::parsePath)
        .anyMatch(path -> PARSED_PATTERNS.stream().anyMatch(pattern -> pattern.matches(path)));
  }

  /**
   * Si la operación modifica datos y no está exceptuada. Un mapeo sin métodos acepta cualquiera,
   * POST incluido, así que también cuenta como escritura.
   */
  public static boolean isWrite(HandlerMethod handler) {
    if (handler.hasMethodAnnotation(AllowedInReadOnlyQuestionnaire.class)) {
      return false;
    }
    RequestMapping mapping =
        AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequestMapping.class);
    if (mapping == null) {
      return false;
    }
    RequestMethod[] methods = mapping.method();
    if (methods.length == 0) {
      return true;
    }
    for (RequestMethod method : methods) {
      if (!READ_METHODS.contains(method)) {
        return true;
      }
    }
    return false;
  }
}
