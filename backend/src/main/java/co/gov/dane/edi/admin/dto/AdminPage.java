package co.gov.dane.edi.admin.dto;

import java.util.List;

public record AdminPage(
    String pageCode, String chapterCode, String tableName, List<AdminEntry> entries) {}
