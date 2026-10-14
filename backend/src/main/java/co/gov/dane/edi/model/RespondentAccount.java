package co.gov.dane.edi.model;

/** Cuenta de un servidor(a) de la muestra. Contiene datos personales: no registrar en logs. */
public record RespondentAccount(
    String respondentCode, String entityCode, String storedPassword, boolean enabledForWeb) {

  @Override
  public String toString() {
    return "RespondentAccount[entityCode=" + entityCode + ", enabledForWeb=" + enabledForWeb + "]";
  }
}
