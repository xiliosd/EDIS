package co.gov.dane.edi.model;

import java.util.List;

/** {@code receipt} solo existe cuando la encuesta está terminada (D17, D18). */
public record SurveyProgress(
    List<PageStatus> pages, boolean finished, boolean observationsSaved, String receipt) {}
