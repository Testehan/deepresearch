package com.testehan.deepresearch.model;

public record ResearchDocumentRequest(
    String pagePrompt,
    String compileReportPrompt
) {
    public static final String DEFAULT_PAGE_PROMPT = """
            You are a research analyst. Analyze page %d of the uploaded document.

            Extract and summarize:
            - Key facts and findings
            - Important data points
            - Any conclusions or recommendations mentioned on this page

            Page content:
            %s
            """;

    public static final String DEFAULT_COMPILE_REPORT_PROMPT = """
            You are a senior research analyst compiling a briefing document based on an uploaded document.

            Based on these extracted findings from each page, produce:
            1. An executive summary of the entire document (2-3 paragraphs)
            2. Key findings across all pages (list of concise bullet points)
            3. Major themes and conclusions
            4. Any discrepancies or notable points found in the document

            Extracted findings:
            %s
            """;

    public String resolvedPagePrompt() {
        return pagePrompt != null ? pagePrompt : DEFAULT_PAGE_PROMPT;
    }

    public String resolvedCompileReportPrompt() {
        return compileReportPrompt != null ? compileReportPrompt : DEFAULT_COMPILE_REPORT_PROMPT;
    }
}
