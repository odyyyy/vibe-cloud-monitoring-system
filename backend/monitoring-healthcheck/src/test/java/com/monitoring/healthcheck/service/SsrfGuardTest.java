package com.monitoring.healthcheck.service;

import com.monitoring.healthcheck.exception.InvalidTargetUrlException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for SSRF protection. Only literal IP addresses are used so no DNS traffic is
 * required and the tests stay deterministic.
 */
class SsrfGuardTest {

    private final SsrfGuard guard = new SsrfGuard();

    @Test
    void blocksLoopbackPrivateLinkLocalAndCgnatAddresses() throws Exception {
        assertFalse(guard.isPublicAddress(InetAddress.getByName("127.0.0.1")));
        assertFalse(guard.isPublicAddress(InetAddress.getByName("10.1.2.3")));
        assertFalse(guard.isPublicAddress(InetAddress.getByName("172.16.5.4")));
        assertFalse(guard.isPublicAddress(InetAddress.getByName("192.168.0.10")));
        assertFalse(guard.isPublicAddress(InetAddress.getByName("169.254.169.254")));
        assertFalse(guard.isPublicAddress(InetAddress.getByName("100.64.0.1")));
        assertFalse(guard.isPublicAddress(InetAddress.getByName("0.0.0.0")));
        assertFalse(guard.isPublicAddress(InetAddress.getByName("224.0.0.1")));
    }

    @Test
    void blocksIpv6LoopbackAndUniqueLocal() throws Exception {
        assertFalse(guard.isPublicAddress(InetAddress.getByName("::1")));
        assertFalse(guard.isPublicAddress(InetAddress.getByName("fc00::1")));
        assertFalse(guard.isPublicAddress(InetAddress.getByName("fd12:3456::1")));
    }

    @Test
    void allowsPublicAddresses() throws Exception {
        assertTrue(guard.isPublicAddress(InetAddress.getByName("8.8.8.8")));
        assertTrue(guard.isPublicAddress(InetAddress.getByName("1.1.1.1")));
        assertTrue(guard.isPublicAddress(InetAddress.getByName("93.184.216.34")));
    }

    @Test
    void parse_rejectsNonHttpSchemes() {
        assertThrows(InvalidTargetUrlException.class, () -> guard.parse("ftp://example.com"));
        assertThrows(InvalidTargetUrlException.class, () -> guard.parse("file:///etc/passwd"));
        assertThrows(InvalidTargetUrlException.class, () -> guard.parse("javascript:alert(1)"));
    }

    @Test
    void parse_rejectsCredentialsAndMissingHost() {
        assertThrows(InvalidTargetUrlException.class,
                () -> guard.parse("http://user:secret@example.com"));
        assertThrows(InvalidTargetUrlException.class, () -> guard.parse("http://"));
        assertThrows(InvalidTargetUrlException.class, () -> guard.parse("   "));
        assertThrows(InvalidTargetUrlException.class, () -> guard.parse("not-a-url"));
    }

    @Test
    void parse_acceptsHttpAndHttps() {
        assertEquals("https://example.com/health",
                guard.parse("https://example.com/health").toString());
        assertEquals("http://example.com:8080/health",
                guard.parse("http://example.com:8080/health").toString());
    }

    @Test
    void validate_rejectsLoopbackAndMetadataLiterals() {
        assertThrows(InvalidTargetUrlException.class, () -> guard.validate("http://127.0.0.1/"));
        assertThrows(InvalidTargetUrlException.class,
                () -> guard.validate("http://169.254.169.254/latest/meta-data/"));
        assertThrows(InvalidTargetUrlException.class, () -> guard.validate("http://[::1]/"));
    }

    @Test
    void validate_acceptsPublicLiteral() {
        URI uri = guard.validate("http://8.8.8.8/health");
        assertEquals("http://8.8.8.8/health", uri.toString());
    }

    @Test
    void assertPublicHost_rejectsHostThatResolvesToLoopback() {
        // "localhost" resolves locally (to 127.0.0.1 / ::1), so no external DNS is needed.
        URI uri = guard.parse("http://localhost/health");
        assertThrows(InvalidTargetUrlException.class, () -> guard.assertPublicHost(uri));
    }
}
