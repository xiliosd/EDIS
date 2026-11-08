package co.gov.dane.edi.operation.report.dto;

import co.gov.dane.edi.operation.report.SampleStatus;

/** Filtro por estado del listado nominal: todos o uno de los cuatro estados del criterio único. */
public enum StatusFilter {
  ALL(null),
  COMPLETE(SampleStatus.COMPLETE),
  INCOMPLETE(SampleStatus.INCOMPLETE),
  NOT_STARTED(SampleStatus.NOT_STARTED),
  NOVELTY(SampleStatus.NOVELTY);

  private final SampleStatus status;

  StatusFilter(SampleStatus status) {
    this.status = status;
  }

  /** Estado que se filtra; nulo si no se filtra. */
  public SampleStatus status() {
    return status;
  }
}
