package com.monitoring.audit.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void prefersTheFirstEntryOfXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1, 172.16.0.2");
        request.setRemoteAddr("10.0.0.1");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void skipsUnknownEntriesInXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "unknown, 198.51.100.9");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("198.51.100.9");
    }

    @Test
    void fallsBackToXRealIpWhenXForwardedForIsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "198.51.100.20");
        request.setRemoteAddr("10.0.0.5");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void fallsBackToRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.30");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("192.0.2.30");
    }

    @Test
    void blanksAndUnknownOnlyHeadersAreIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "   , unknown ");
        request.setRemoteAddr("192.0.2.44");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("192.0.2.44");
    }

    @Test
    void truncatesLongValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String longValue = "a".repeat(200);
        request.addHeader("X-Forwarded-For", longValue);

        assertThat(ClientIpResolver.resolve(request)).hasSize(64);
    }

    @Test
    void returnsNullWithoutRequest() {
        assertThat(ClientIpResolver.resolve(null)).isNull();
    }
}
