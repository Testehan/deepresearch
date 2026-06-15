package com.testehan.deepresearch.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "reportType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = NewsReport.class, name = "news"),
        @JsonSubTypes.Type(value = EarningsPresentationReport.class, name = "earnings_presentation"),
        @JsonSubTypes.Type(value = BatchPromptsResult.class, name = "batch_prompts")
})
public interface ReportResult {
}
