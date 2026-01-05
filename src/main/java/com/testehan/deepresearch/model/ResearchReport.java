package com.testehan.deepresearch.model;

import java.util.List;

public record ResearchReport(
        String topic,
        String executiveSummary,
        List<String> keyFindings,
        List<String> themes,
        List<String> openQuestions,
        List<SourceReference> sources,
        Diagnostics diagnostics
) {}