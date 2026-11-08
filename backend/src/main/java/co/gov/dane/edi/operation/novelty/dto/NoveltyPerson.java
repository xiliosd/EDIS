package co.gov.dane.edi.operation.novelty.dto;

import co.gov.dane.edi.operation.report.SampleStatus;

/**
 * Funcionario de la muestra con su novedad actual. Contiene datos personales: solo viaja en
 * respuestas {@code no-store}. {@code otherCause} llega decodificado (sin entidades {@code &#N;}) y
 * es el valor que la pantalla devuelve como esperado al modificar o eliminar.
 */
public record NoveltyPerson(
    String respondentCode,
    String name,
    Integer level,
    SampleStatus status,
    Integer noveltyCode,
    String otherCause) {}
