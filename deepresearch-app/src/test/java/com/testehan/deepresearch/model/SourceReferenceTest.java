package com.testehan.deepresearch.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceReferenceTest {

    @Test
    void shouldCreateSourceReference() {
        var ref = new SourceReference("http://example.com", "Example");
        assertEquals("http://example.com", ref.url());
        assertEquals("Example", ref.title());
    }

    @Test
    void shouldHandleNullValues() {
        var ref = new SourceReference(null, null);
        assertNull(ref.url());
        assertNull(ref.title());
    }
}