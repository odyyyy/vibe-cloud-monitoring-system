package com.monitoring.auth.config;

import com.monitoring.auth.security.SsoAuthenticationSuccessHandler;
import com.monitoring.auth.security.SsoOidcUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Opt-in security chain for OpenID Connect login.
 *
 * <p>Everything is created only when {@code app.sso.enabled=true}, so a deployment without SSO
 * has no dependency on an identity provider. The chain matches only the SSO entry points and uses
 * a session (required by the OAuth2 authorization-request state); every other request is handled
 * by the stateless JWT chain in {@link SecurityConfig}.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(prefix = "app.sso", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Order(1)
public class SsoSecurityConfig {

    private final SsoProperties ssoProperties;
    private final SsoOidcUserService ssoOidcUserService;
    private final SsoAuthenticationSuccessHandler successHandler;

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration registration = ClientRegistrations
                .fromIssuerLocation(ssoProperties.getIssuerUri())
                .registrationId(ssoProperties.getRegistrationId())
                .clientId(ssoProperties.getClientId())
                .clientSecret(ssoProperties.getClientSecret())
                .scope(ssoProperties.getScopes())
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain ssoFilterChain(HttpSecurity http) throws Exception {
        http
            // Only the SSO entry points and Spring Security's OAuth2 endpoints are handled here.
            .securityMatcher(
                    "/api/auth/sso/**",
                    "/oauth2/authorization/**",
                    "/login/oauth2/**")
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            // The OAuth2 authorization request is kept in the session for the duration of the
            // redirect; the session is invalidated on success, so nothing survives the login.
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .oauth2Login(oauth -> oauth
                    .userInfoEndpoint(info -> info.oidcUserService(ssoOidcUserService))
                    .successHandler(successHandler)
                    .failureHandler((request, response, exception) ->
                            response.sendRedirect(ssoProperties.getFailureRedirectUrl() + "?error=sso")));

        return http.build();
    }
}
