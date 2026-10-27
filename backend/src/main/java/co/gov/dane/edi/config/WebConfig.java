package co.gov.dane.edi.config;

import co.gov.dane.edi.admin.YearCatalogService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  // ObjectProvider: en pruebas de capa web (@WebMvcTest) no existe el catálogo de años.
  private final ObjectProvider<YearCatalogService> years;

  public WebConfig(ObjectProvider<YearCatalogService> years) {
    this.years = years;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    years.ifAvailable(
        catalog ->
            registry
                .addInterceptor(new SurveyAvailabilityInterceptor(catalog))
                .addPathPatterns("/api/v1/survey/**"));
  }
}
