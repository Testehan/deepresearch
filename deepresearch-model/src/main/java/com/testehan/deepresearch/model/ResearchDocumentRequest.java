package com.testehan.deepresearch.model;

public record ResearchDocumentRequest(
    ResearchTopic topic,
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
            You are a senior financial analyst compiling an earnings presentation briefing.
            Based on the extracted findings from all pages of the earnings presentation, produce a comprehensive report in JSON format.

            The JSON must follow this exact structure:
            {
              "company_metadata": {
                "company_name": "Name of the company",
                "report_period": "The quarter and/or year of the report (e.g., Q4 2025, FY 2024)"
              },
              "headline_financials": {
                "total_revenue": "Total revenue/sales for the primary reported period",
                "yoy_revenue_growth": "Year-over-year revenue growth percentage",
                "gross_margin": "Gross margin percentage (GAAP or Non-GAAP)",
                "operating_margin": "Operating margin percentage",
                "ebitda_or_adj_ebitda": "EBITDA or Adjusted EBITDA value",
                "net_income": "Net Income for the period",
                "eps_earnings_per_share": "Earnings Per Share (EPS)"
              },
              "cash_and_capital": {
                "free_cash_flow": "Free cash flow generated",
                "operating_cash_flow": "Cash from operations",
                "cash_and_equivalents": "Total cash, equivalents, and short-term investments on the balance sheet"
              },
              "industry_specific_kpis": [
                {
                  "metric_name": "Name of the operational metric",
                  "metric_value": "Value for the current period",
                  "yoy_change": "Year-over-year change or growth of this metric (use null if not provided)"
                }
              ],
              "market_and_growth_opportunity": {
                "total_addressable_market_tam": "Dollar value of the TAM mentioned, if any",
                "geographic_revenue_split": "Brief summary of revenue by geography or international growth"
              },
              "revenue_quality_and_risk": {
                "recurring_revenue_percentage": "Percentage of revenue that is recurring or subscription-based",
                "customer_concentration": "Any data on revenue concentration among top customers",
                "macro_factors_mentioned": [
                  "List of macro headwinds or tailwinds explicitly mentioned (e.g., FX impact, inflation, interest rates, supply chain)"
                ]
              },
              "debt_and_leverage": {
                "total_debt": "Total outstanding debt",
                "net_leverage_ratio": "Net debt to EBITDA ratio, if provided",
                "upcoming_debt_maturities": "Details on when major debt is due or needs refinancing"
              },
              "corporate_actions": {
                "mergers_and_acquisitions": "Brief summary of any M&A activity, targets, or expected synergies mentioned",
                "cost_saving_initiatives": "Details on restructuring, headcount reductions, or efficiency programs",
                "shareholder_returns": "Details on dividends paid, dividend yields, or share repurchase (buyback) programs"
              },
              "forward_looking_guidance": {
                "next_period_revenue_guidance": "Expected revenue for the upcoming quarter/period",
                "full_year_revenue_guidance": "Expected revenue for the upcoming full fiscal year",
                "eps_guidance": "Expected EPS for the next period or full year",
                "capex_guidance": "Expected Capital Expenditures (CapEx)"
              },
              "strategic_highlights": [
                "Extract 3 to 5 brief bullet points highlighting management's overarching strategic narrative, major product launches, or future goals mentioned in the text."
              ]
            }

            If a piece of information is not available in the provided text, use null for single values or an empty list for arrays.
            Do not include any text before or after the JSON block.

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
