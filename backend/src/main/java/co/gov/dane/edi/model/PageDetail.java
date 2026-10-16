package co.gov.dane.edi.model;

import java.util.List;
import java.util.Map;

public record PageDetail(
    String pageCode,
    String chapterCode,
    String chapterTitle,
    String module,
    PageState state,
    List<QuestionnaireEntry> entries,
    Map<String, String> answers) {}
