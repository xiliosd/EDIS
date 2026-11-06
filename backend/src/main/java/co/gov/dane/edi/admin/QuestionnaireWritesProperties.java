package co.gov.dane.edi.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Si este ambiente puede cambiar el cuestionario (FRM_*, GRAL_AYUDAS y el archivo de reglas) desde
 * la administración. En producción va en false: el usuario de Oracle de la aplicación tiene más
 * permisos de los que el diseño le da, y la regla es que el cuestionario solo cambia en QA y llega
 * a producción con el paquete de promoción. Un valor que no sea booleano detiene el arranque; si
 * falta, queda en false (seguro por defecto).
 */
@Validated
@ConfigurationProperties("edi.admin")
public record QuestionnaireWritesProperties(boolean questionnaireWritesEnabled) {}
