package co.gov.dane.edi.config;

import co.gov.dane.edi.admin.QuestionnaireWriteGuard;
import co.gov.dane.edi.admin.QuestionnaireWritesProperties;
import co.gov.dane.edi.admin.YearCatalogService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  // ObjectProvider: en pruebas de capa web (@WebMvcTest) no existe el catálogo de años.
  private final ObjectProvider<YearCatalogService> years;
  private final ObjectProvider<QuestionnaireWritesProperties> questionnaireWrites;

  public WebConfig(
      ObjectProvider<YearCatalogService> years,
      ObjectProvider<QuestionnaireWritesProperties> questionnaireWrites) {
    this.years = years;
    this.questionnaireWrites = questionnaireWrites;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    years.ifAvailable(
        catalog ->
            registry
                .addInterceptor(new SurveyAvailabilityInterceptor(catalog))
                .addPathPatterns("/api/v1/survey/**"));
    // Sin la propiedad registrada se asume solo lectura: el error seguro es no dejar escribir.
    QuestionnaireWritesProperties writes =
        questionnaireWrites.getIfAvailable(() -> new QuestionnaireWritesProperties(false));
    registry
        .addInterceptor(new QuestionnaireWriteGuard(writes))
        .addPathPatterns(QuestionnaireWriteGuard.PATH_PATTERNS);
  }
}
