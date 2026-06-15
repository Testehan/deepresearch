package com.testehan.deepresearch.model;

import java.util.List;

public record BatchPromptsResult(
        List<BatchPromptResult> results
) implements ReportResult {}
