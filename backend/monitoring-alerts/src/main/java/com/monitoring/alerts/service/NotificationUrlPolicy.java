package com.monitoring.alerts.service;

import com.monitoring.healthcheck.service.SsrfGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Shared URL policy for customer-supplied notification endpoints.
 *
 * <p>HTTPS is required in every environment. Plain HTTP is accepted only when the explicit
 * {@code monitoring.alerts.notifications.allow-insecure-http} development flag is enabled, and
 * even then the host must still resolve exclusively to public addresses. Loopback, private,
 * link-local and cloud-metadata addresses are therefore always rejected.
 */
@Component
public class NotificationUrlPolicy {

    private static final String HTTPS = "https";
    private static final String HTTP = "http";

    private final SsrfGuard ssrfGuard;
    private final boolean allowInsecureHttp;

    public NotificationUrlPolicy(
            SsrfGuard ssrfGuard,
            @Value("${monitoring.alerts.notifications.allow-insecure-http:false}") boolean allowInsecureHttp) {
        this.ssrfGuard = ssrfGuard;
        this.allowInsecureHttp = allowInsecureHttp;
    }

    /**
     * @param rawUrl the customer-supplied URL
     * @param label  human-readable type used in the error message (for example "Webhook")
     * @return the parsed URI
     * @throws IllegalArgumentException when the scheme or resolved host is not allowed
     */
    public URI validatePublicHttpsUrl(String rawUrl, String label) {
        URI uri = ssrfGuard.parse(rawUrl);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!HTTPS.equals(scheme) && !(allowInsecureHttp && HTTP.equals(scheme))) {
            throw new IllegalArgumentException(label + " URL must use https");
        }
        ssrfGuard.assertPublicHost(uri);
        return uri;
    }
}
