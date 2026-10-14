package co.gov.dane.edi.config;

import co.gov.dane.edi.security.SessionTokenService;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  static final String SESSION_SCHEME = "sessionCookie";

  @Bean
  OpenAPI formularioOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("API Formulario EDI/EDID")
                .version("v1")
                .description(
                    "API de la Encuesta sobre Ambiente y Desempeño Institucional (DANE). "
                        + "Las operaciones que modifican datos exigen la cabecera X-XSRF-TOKEN."))
        .components(
            new Components()
                .addSecuritySchemes(
                    SESSION_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(SessionTokenService.SESSION_COOKIE_NAME)))
        .addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME));
  }
}
