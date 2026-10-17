package com.monitoring.collector.security;

import com.monitoring.storage.entity.Application;
import com.monitoring.storage.repository.ApplicationRepository;
import com.monitoring.storage.security.ApiKeyHasher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates agents by the API key from the {@code X-API-Key} header.
 *
 * <ul>
 *     <li>missing/invalid key: HTTP 401 and the chain stops;</li>
 *     <li>valid key: an authentication with principal = {@link Application} and authority
 *     {@code ROLE_AGENT} is placed into the {@link SecurityContextHolder}.</li>
 * </ul>
 *
 * <p>The filter is intentionally not a Spring bean: it is created inside
 * {@link IngestSecurityConfig} and attached only to the ingestion chain. A bean filter would be
 * auto-registered by the container for every request and would reject requests without the
 * header. The key itself is never logged.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final ApplicationRepository applicationRepository;

    public ApiKeyAuthFilter(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            reject(response, "Missing API key");
            return;
        }

        // Only the hash of the key is stored, so hash the presented value before lookup.
        Optional<Application> application = applicationRepository.findByApiKeyHash(ApiKeyHasher.hash(apiKey));
        if (application.isEmpty()) {
            reject(response, "Invalid API key");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                application.get(), null, List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":401,\"message\":\"" + message + "\"}");
    }
}
