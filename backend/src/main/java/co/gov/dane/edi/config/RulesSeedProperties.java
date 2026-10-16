package co.gov.dane.edi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Carpeta opcional con semillas de reglas de desarrollo. Vacía en QA y producción: allí manda la
 * semilla del classpath, que solo contiene reglas del cuestionario real.
 */
@ConfigurationProperties("app.rules-seed")
public record RulesSeedProperties(String dir) {}
