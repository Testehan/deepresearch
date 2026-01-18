package com.testehan.deepresearch.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

@JsonTypeName("earnings_presentation")
public record EarningsPresentationReport(
    @JsonProperty("company_metadata") CompanyMetadata companyMetadata,
    @JsonProperty("headline_financials") HeadlineFinancials headlineFinancials,
    @JsonProperty("cash_and_capital") CashAndCapital cashAndCapital,
    @JsonProperty("industry_specific_kpis") List<IndustrySpecificKpi> industrySpecificKpis,
    @JsonProperty("market_and_growth_opportunity") MarketAndGrowthOpportunity marketAndGrowthOpportunity,
    @JsonProperty("revenue_quality_and_risk") RevenueQualityAndRisk revenueQualityAndRisk,
    @JsonProperty("debt_and_leverage") DebtAndLeverage debtAndLeverage,
    @JsonProperty("corporate_actions") CorporateActions corporateActions,
    @JsonProperty("forward_looking_guidance") ForwardLookingGuidance forwardLookingGuidance,
    @JsonProperty("strategic_highlights") List<String> strategicHighlights
) implements ReportResult {

    public record CompanyMetadata(
        @JsonProperty("company_name") String companyName,
        @JsonProperty("report_period") String reportPeriod
    ) {}

    public record HeadlineFinancials(
        @JsonProperty("total_revenue") String totalRevenue,
        @JsonProperty("yoy_revenue_growth") String yoyRevenueGrowth,
        @JsonProperty("gross_margin") String grossMargin,
        @JsonProperty("operating_margin") String operatingMargin,
        @JsonProperty("ebitda_or_adj_ebitda") String ebitdaOrAdjEbitda,
        @JsonProperty("net_income") String netIncome,
        @JsonProperty("eps_earnings_per_share") String epsEarningsPerShare
    ) {}

    public record CashAndCapital(
        @JsonProperty("free_cash_flow") String freeCashFlow,
        @JsonProperty("operating_cash_flow") String operatingCashFlow,
        @JsonProperty("cash_and_equivalents") String cashAndEquivalents
    ) {}

    public record IndustrySpecificKpi(
        @JsonProperty("metric_name") String metricName,
        @JsonProperty("metric_value") String metricValue,
        @JsonProperty("yoy_change") String yoyChange
    ) {}

    public record MarketAndGrowthOpportunity(
        @JsonProperty("total_addressable_market_tam") String totalAddressableMarketTam,
        @JsonProperty("geographic_revenue_split") String geographicRevenueSplit
    ) {}

    public record RevenueQualityAndRisk(
        @JsonProperty("recurring_revenue_percentage") String recurringRevenuePercentage,
        @JsonProperty("customer_concentration") String customerConcentration,
        @JsonProperty("macro_factors_mentioned") List<String> macroFactorsMentioned
    ) {}

    public record DebtAndLeverage(
        @JsonProperty("total_debt") String totalDebt,
        @JsonProperty("net_leverage_ratio") String netLeverageRatio,
        @JsonProperty("upcoming_debt_maturities") String upcomingDebtMaturities
    ) {}

    public record CorporateActions(
        @JsonProperty("mergers_and_acquisitions") String mergersAndAcquisitions,
        @JsonProperty("cost_saving_initiatives") String costSavingInitiatives,
        @JsonProperty("shareholder_returns") String shareholderReturns
    ) {}

    public record ForwardLookingGuidance(
        @JsonProperty("next_period_revenue_guidance") String nextPeriodRevenueGuidance,
        @JsonProperty("full_year_revenue_guidance") String fullYearRevenueGuidance,
        @JsonProperty("eps_guidance") String epsGuidance,
        @JsonProperty("capex_guidance") String capexGuidance
    ) {}
}
