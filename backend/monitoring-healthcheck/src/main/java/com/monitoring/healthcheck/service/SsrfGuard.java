package com.monitoring.healthcheck.service;

import com.monitoring.healthcheck.exception.InvalidTargetUrlException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Guards synthetic checks against Server-Side Request Forgery.
 *
 * <p>Only absolute {@code http}/{@code https} URLs without embedded credentials are accepted,
 * and the host must resolve exclusively to public addresses. Loopback, private, link-local,
 * site-local, multicast, CGNAT, benchmarking, documentation, tunneling (6to4, NAT64, Teredo),
 * unspecified and IPv6 unique-local ranges are rejected so a customer cannot make the platform
 * probe internal networks or cloud metadata endpoints (for example {@code 169.254.169.254}).
 *
 * <p>Redirects are disabled by the executor, so a check only ever talks to the validated host.
 * The URL is validated again immediately before each execution to catch hosts that start
 * resolving to a private address after configuration.
 *
 * <h2>DNS rebinding / TOCTOU</h2>
 * The hard guarantee is that <em>every</em> address the host resolves to must be public. There
 * is still an inherent time-of-check/time-of-use gap: {@link #assertPublicHost} resolves the
 * host and the underlying client resolves it again when it opens the connection, so a host that
 * changes its answer between the two lookups could theoretically reach an internal address.
 * The window is bounded in practice by validating as late as possible (immediately before the
 * request), rejecting hosts that resolve to any non-public address, disabling redirects, and
 * relying on the JVM's positive DNS cache ({@code networkaddress.cache.ttl}) so the address used
 * for the connection is the one that was just validated. Fully eliminating the gap requires an
 * HTTP client with a pluggable resolver (which {@code java.net.http.HttpClient} does not offer);
 * the {@link HealthCheckExecutor} documents that residual risk.
 */
@Component
public class SsrfGuard {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /**
     * Validates the syntactic shape of the target URL. Does not perform DNS resolution.
     *
     * @return the parsed URI
     * @throws InvalidTargetUrlException if the URL is not allowed
     */
    public URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidTargetUrlException("Target URL is required");
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException ex) {
            throw new InvalidTargetUrlException("Target URL is not a valid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new InvalidTargetUrlException("Only http and https URLs are allowed");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidTargetUrlException("Target URL must not contain credentials");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidTargetUrlException("Target URL must include a host");
        }
        int port = uri.getPort();
        if (port == 0 || port > 65535) {
            throw new InvalidTargetUrlException("Target URL has an invalid port");
        }
        return uri;
    }

    /**
     * Resolves the URI host and rejects it if any resolved address is not public.
     *
     * @throws InvalidTargetUrlException if the host cannot be resolved or is not public
     */
    public void assertPublicHost(URI uri) {
        String host = uri.getHost();
        if (host != null && host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new InvalidTargetUrlException("Target host could not be resolved");
        }
        if (addresses.length == 0) {
            throw new InvalidTargetUrlException("Target host could not be resolved");
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new InvalidTargetUrlException("Target host resolves to a non-public address");
            }
        }
    }

    /** Convenience: parse then resolve. */
    public URI validate(String rawUrl) {
        URI uri = parse(rawUrl);
        assertPublicHost(uri);
        return uri;
    }

    /**
     * @return {@code true} if the address is routable on the public internet and safe to probe
     */
    boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            if (isIpv4Mapped(bytes)) {
                return isPublicIpv4(bytes[12], bytes[13], bytes[14], bytes[15]);
            }
            // IPv6 unique local addresses fc00::/7 are not covered by isSiteLocalAddress.
            if ((bytes[0] & 0xfe) == 0xfc) {
                return false;
            }
            // 6to4 2002::/16 embeds an IPv4 address and can tunnel to private destinations.
            if ((bytes[0] & 0xff) == 0x20 && (bytes[1] & 0xff) == 0x02) {
                return false;
            }
            // NAT64 64:ff9b::/96 and 64:ff9b:1::/48 translate to IPv4 and can embed private ones.
            if (isNat64(bytes)) {
                return false;
            }
            // Teredo 2001::/32 tunnels IPv4 traffic.
            if ((bytes[0] & 0xff) == 0x20 && (bytes[1] & 0xff) == 0x01
                    && bytes[2] == 0 && bytes[3] == 0) {
                return false;
            }
            // 2001:db8::/32 documentation range.
            if ((bytes[0] & 0xff) == 0x20 && (bytes[1] & 0xff) == 0x01
                    && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8) {
                return false;
            }
            return true;
        }
        return isPublicIpv4(bytes[0], bytes[1], bytes[2], bytes[3]);
    }

    private boolean isNat64(byte[] bytes) {
        if ((bytes[0] & 0xff) != 0x00 || (bytes[1] & 0xff) != 0x64
                || (bytes[2] & 0xff) != 0xff || (bytes[3] & 0xff) != 0x9b) {
            return false;
        }
        // 64:ff9b::/96
        boolean middleZero = true;
        for (int i = 4; i < 12; i++) {
            if (bytes[i] != 0) {
                middleZero = false;
                break;
            }
        }
        // 64:ff9b:1::/48
        boolean hasLocalPrefix = (bytes[4] & 0xff) == 0x00 && (bytes[5] & 0xff) == 0x01;
        return middleZero || hasLocalPrefix;
    }

    private boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
    }

    private boolean isPublicIpv4(byte b0, byte b1, byte b2, byte b3) {
        int a = b0 & 0xff;
        int second = b1 & 0xff;
        int third = b2 & 0xff;
        if (a == 0) {
            return false;
        }
        if (a == 10) {
            return false;
        }
        if (a == 127) {
            return false;
        }
        if (a == 169 && second == 254) {
            return false;
        }
        if (a == 172 && second >= 16 && second <= 31) {
            return false;
        }
        if (a == 192 && second == 168) {
            return false;
        }
        // CGNAT 100.64.0.0/10
        if (a == 100 && second >= 64 && second <= 127) {
            return false;
        }
        // 192.0.0.0/24 IETF protocol assignments
        if (a == 192 && second == 0 && third == 0) {
            return false;
        }
        // 192.0.2.0/24 TEST-NET-1, 198.51.100.0/24 TEST-NET-2, 203.0.113.0/24 TEST-NET-3
        if (a == 192 && second == 0 && third == 2) {
            return false;
        }
        if (a == 198 && second == 51 && third == 100) {
            return false;
        }
        if (a == 203 && second == 0 && third == 113) {
            return false;
        }
        // 192.88.99.0/24 6to4 relay anycast
        if (a == 192 && second == 88 && third == 99) {
            return false;
        }
        // 198.18.0.0/15 benchmarking
        if (a == 198 && (second == 18 || second == 19)) {
            return false;
        }
        // 224.0.0.0/4 multicast and above
        if (a >= 224) {
            return false;
        }
        return true;
    }
}
