package co.gov.dane.edi.model;

import java.util.List;

public record ChapterDetail(
    String code, String title, String module, List<QuestionnaireEntry> entries) {}
