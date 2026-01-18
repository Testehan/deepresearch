package com.testehan.deepresearch.model;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;

@JsonTypeName("news")
public record NewsReport(
        ResearchTopic topic,
        String executiveSummary,
        List<String> keyFindings,
        List<String> themes,
        List<String> openQuestions,
        List<SourceReference> sources,
        Diagnostics diagnostics
) implements ReportResult {}
