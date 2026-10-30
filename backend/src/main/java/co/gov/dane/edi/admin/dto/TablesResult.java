package co.gov.dane.edi.admin.dto;

import java.util.List;
import java.util.Map;

/**
 * Mapeo guardado y nueva versión del año. {@code missing}: capítulos cuya tabla aún no existe en el
 * esquema; se crea con el script de cada capítulo antes de publicar el año.
 */
public record TablesResult(int version, Map<String, String> tables, List<String> missing) {}
