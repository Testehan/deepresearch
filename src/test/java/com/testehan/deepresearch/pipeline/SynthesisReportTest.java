package com.testehan.deepresearch.pipeline;

import com.testehan.deepresearch.model.FetchedSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SynthesisReportTest {

    @Test
    void shouldCreateReport() {
        var report = new SynthesisService.Report(
                "Executive summary",
                List.of("Finding 1", "Finding 2"),
                List.of("Theme 1"),
                List.of("Question 1")
        );

        assertEquals("Executive summary", report.executiveSummary());
        assertEquals(2, report.keyFindings().size());
        assertEquals(1, report.themes().size());
        assertEquals(1, report.openQuestions().size());
    }

    @Test
    void shouldHandleEmptyLists() {
        var report = new SynthesisService.Report("summary", List.of(), List.of(), List.of());

        assertTrue(report.keyFindings().isEmpty());
        assertTrue(report.themes().isEmpty());
        assertTrue(report.openQuestions().isEmpty());
    }

    @Test
    void shouldHandleNullInLists() {
        var report = new SynthesisService.Report("summary", null, null, null);

        assertNull(report.keyFindings());
        assertNull(report.themes());
        assertNull(report.openQuestions());
    }
}