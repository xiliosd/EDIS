package co.gov.dane.edi.config;

import co.gov.dane.edi.admin.QuestionnaireWriteGuard;
import co.gov.dane.edi.security.SessionTokenService;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
public class OpenApiConfig {

  static final String SESSION_SCHEME = "sessionCookie";

  static final String READ_ONLY_DESCRIPTION =
      "Cuestionario de solo lectura en este ambiente: los cambios se hacen en QA y llegan con un"
          + " paquete de promoción";

  @Bean
  OpenAPI formularioOpenApi() {
    return new OpenAPI()
        .servers(List.of(new Server().url("/")))
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

  /**
   * Documenta el 403 de solo lectura con el mismo criterio de la compuerta (rutas y métodos), así
   * el contrato no se desfasa cuando aparece una operación de escritura nueva.
   */
  @Bean
  OperationCustomizer readOnlyQuestionnaireResponse(
      @Qualifier("requestMappingHandlerMapping")
          ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
    return (operation, handler) -> {
      if (QuestionnaireWriteGuard.isWrite(handler)
          && QuestionnaireWriteGuard.covers(patternsOf(handlerMappings.getObject(), handler))) {
        if (operation.getResponses() == null) {
          operation.setResponses(new ApiResponses());
        }
        operation
            .getResponses()
            .addApiResponse(
                "403",
                new ApiResponse()
                    .description(READ_ONLY_DESCRIPTION)
                    .content(
                        new Content()
                            .addMediaType(
                                "application/problem+json",
                                new MediaType()
                                    .schema(
                                        new Schema<>()
                                            .$ref("#/components/schemas/ProblemDetail")))));
      }
      return operation;
    };
  }

  private static Set<String> patternsOf(
      RequestMappingHandlerMapping handlerMappings, HandlerMethod handler) {
    return handlerMappings.getHandlerMethods().entrySet().stream()
        .filter(entry -> entry.getValue().getMethod().equals(handler.getMethod()))
        .flatMap(entry -> entry.getKey().getPatternValues().stream())
        .collect(Collectors.toSet());
  }
}
