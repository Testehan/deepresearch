package com.testehan.deepresearch.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ResearchTopic {
    NEWS("news"),
    EARNINGS_PRESENTATION("earnings_presentation");

    private final String value;

    ResearchTopic(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ResearchTopic fromValue(String value) {
        for (ResearchTopic topic : values()) {
            if (topic.value.equalsIgnoreCase(value)) {
                return topic;
            }
        }
        throw new IllegalArgumentException("Unknown research topic: " + value);
    }
}
