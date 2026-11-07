package co.gov.dane.edi.model;

/**
 * Ayuda que ve el encuestado (FRM_PRE_AYUDA o FRM_ENC_AYUDA con su definición de GRAL_AYUDAS):
 * {@code term} es la palabra o frase del texto que abre la ayuda, {@code title} el TITULO y {@code
 * definition} la DESCRIPCION. Todo en texto plano: los saltos de línea de la definición van como
 * "\n" y el cliente nunca recibe HTML.
 */
public record TermHelp(String term, String title, String definition) {}
