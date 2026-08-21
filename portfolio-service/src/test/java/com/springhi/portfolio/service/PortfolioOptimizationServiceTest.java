package com.springhi.portfolio.service;

import com.springhi.portfolio.repository.PortfolioRecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioOptimizationServiceTest {

    private PortfolioOptimizationService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioOptimizationService(
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    void testExtractConfidence_Success() {
        String rawText = "[{\"t\":\"AAPL\",\"n\":\"Apple\",\"s\":\"Tech\",\"action\":\"BUY\",\"w\":50,\"r\":\"Rationale\"}]\nconfidence=85";
        Integer confidence = service.extractConfidence(rawText);
        assertEquals(85, confidence);
    }

    @Test
    void testExtractConfidence_WithSpaces() {
        String rawText = "some other text\n  confidence = 92  \nmore text";
        Integer confidence = service.extractConfidence(rawText);
        assertEquals(92, confidence);
    }

    @Test
    void testExtractConfidence_NotFound() {
        String rawText = "[{\"t\":\"AAPL\"}]";
        Integer confidence = service.extractConfidence(rawText);
        assertNull(confidence);
    }

    @Test
    void testExtractConfidence_Clamped() {
        String rawText = "confidence=150";
        Integer confidence = service.extractConfidence(rawText);
        assertEquals(100, confidence);

        rawText = "confidence=-10";
        // The regex \\d{1,3} won't match a minus sign, so it should be null unless I change the regex.
        // But the logic has Math.max(0, v) just in case.
        confidence = service.extractConfidence(rawText);
        assertNull(confidence); 
    }
    
    @Test
    void testExtractConfidence_NullInput() {
        assertNull(service.extractConfidence(null));
    }
}
