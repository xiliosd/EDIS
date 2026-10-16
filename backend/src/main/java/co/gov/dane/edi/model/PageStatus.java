package co.gov.dane.edi.model;

public record PageStatus(
    String pageCode, String chapterCode, String chapterTitle, String module, PageState state) {}
