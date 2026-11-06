package co.gov.dane.edi.admin;

/** Se intentó cambiar el cuestionario en un ambiente de solo lectura (HTTP 403). */
public class QuestionnaireReadOnlyException extends RuntimeException {

  public static final String TITLE = "Cuestionario de solo lectura";

  public static final String MESSAGE =
      "En este ambiente el cuestionario es de solo lectura: los cambios se hacen en QA y llegan con"
          + " un paquete de promoción.";

  public QuestionnaireReadOnlyException() {
    super(MESSAGE);
  }
}
