package co.gov.dane.edi.admin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excepción explícita a {@link QuestionnaireWriteGuard}: la operación modifica datos pero se
 * permite con el cuestionario en solo lectura. Solo la lleva la importación del paquete de
 * promoción, que es justamente el camino por el que producción recibe los cambios hechos en QA.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AllowedInReadOnlyQuestionnaire {}
