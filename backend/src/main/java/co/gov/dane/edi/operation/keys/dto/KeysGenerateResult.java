package co.gov.dane.edi.operation.keys.dto;

/** Cuántos funcionarios recibieron usuario y clave. Nunca incluye las claves. */
public record KeysGenerateResult(long generated) {}
