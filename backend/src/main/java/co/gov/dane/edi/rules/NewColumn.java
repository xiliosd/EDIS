package co.gov.dane.edi.rules;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Definición de una columna de respuesta que la administración pidió crear en un año: tipo
 * (VARCHAR2 o NUMBER) y longitud (o precisión). La aplicación no ejecuta el DDL (D3): sirve para
 * generar el script y para el entregable de producción.
 */
public record NewColumn(
    @JsonProperty("tipo") String type, @JsonProperty("longitud") Integer length) {}
