package co.gov.dane.edi.repository;

/**
 * Fila de FRM_PRE_AYUDA o FRM_ENC_AYUDA tal como está en la base. {@code ownerId} es la pregunta
 * (COD_CAP_PROY) o el encabezado (COD_ENC); {@code hasDefinition} es falso si GRAL_AYUDAS no tiene
 * el COD_AYU, y entonces {@code title} y {@code description} son nulos.
 */
public record HelpRow(
    long ownerId, String term, boolean hasDefinition, String title, String description) {}
