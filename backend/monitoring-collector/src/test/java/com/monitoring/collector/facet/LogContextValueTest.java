package com.monitoring.collector.facet;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The facet filter matches the stored document textually, so the needle has to be encoded exactly
 * the way the agent's Go encoder writes it; otherwise "top values" and "click to filter" disagree.
 */
class LogContextValueTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void encodesAPlainMember() {
        assertEquals("\"host\":\"server-1\"", LogContextValue.member("host", "server-1"));
    }

    @Test
    void escapesQuotesAndBackslashes() {
        assertEquals("\"host\":\"srv\\\"1\"", LogContextValue.member("host", "srv\"1"));
        assertEquals("\"host\":\"a\\\\b\"", LogContextValue.member("host", "a\\b"));
    }

    @Test
    void escapesHtmlSensitiveCharactersLikeGo() {
        assertEquals("\"host\":\"a\\u0026b\"", LogContextValue.member("host", "a&b"));
        assertEquals("\"host\":\"a\\u003cb\"", LogContextValue.member("host", "a<b"));
        assertEquals("\"host\":\"a\\u003eb\"", LogContextValue.member("host", "a>b"));
    }

    @Test
    void escapesControlCharacters() {
        assertEquals("\"host\":\"a\\nb\"", LogContextValue.member("host", "a\nb"));
        assertEquals("\"host\":\"a\\tb\"", LogContextValue.member("host", "a\tb"));
        assertEquals("\"host\":\"a\\u0001b\"", LogContextValue.member("host", "a\u0001b"));
    }

    @Test
    void extractsAValueFromADocument() {
        assertEquals("server-1", LogContextValue.extract(objectMapper,
                "{\"host\":\"server-1\",\"service\":\"api\"}", "host"));
        assertEquals("prod", LogContextValue.extract(objectMapper,
                "{\"k8s.namespace\":\"prod\"}", "k8s.namespace"));
        assertEquals("42", LogContextValue.extract(objectMapper, "{\"host\":42}", "host"));
    }

    @Test
    void decodesEscapedValues() {
        assertEquals("srv\"1", LogContextValue.extract(objectMapper, "{\"host\":\"srv\\\"1\"}", "host"));
        assertEquals("a&b", LogContextValue.extract(objectMapper, "{\"host\":\"a\\u0026b\"}", "host"));
    }

    @Test
    void ignoresMissingInvalidOrNonScalarValues() {
        assertNull(LogContextValue.extract(objectMapper, null, "host"));
        assertNull(LogContextValue.extract(objectMapper, "  ", "host"));
        assertNull(LogContextValue.extract(objectMapper, "not json", "host"));
        assertNull(LogContextValue.extract(objectMapper, "[1,2]", "host"));
        assertNull(LogContextValue.extract(objectMapper, "{\"service\":\"api\"}", "host"));
        assertNull(LogContextValue.extract(objectMapper, "{\"host\":null}", "host"));
        assertNull(LogContextValue.extract(objectMapper, "{\"host\":{\"a\":1}}", "host"));
        assertNull(LogContextValue.extract(objectMapper, "{\"host\":[1]}", "host"));
    }
}
