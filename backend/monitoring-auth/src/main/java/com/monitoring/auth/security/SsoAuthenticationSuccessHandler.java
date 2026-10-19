package com.monitoring.auth.security;

import com.monitoring.auth.config.SsoProperties;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.jwt.JwtTokenProvider;
import com.monitoring.auth.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Turns a successful OIDC login into an internal JWT and hands it to the frontend.
 *
 * <p>The JWT is placed in the URL fragment (never the query string) so it is not sent to the
 * server, not written to access logs and not leaked through the {@code Referer} header. The
 * transient session that held the IDP authorization state is invalidated so no provider token
 * outlives the login.
 */
@Component
@ConditionalOnProperty(prefix = "app.sso", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SsoAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final SsoProperties ssoProperties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser principal = (OidcUser) authentication.getPrincipal();
        String email = principal.getEmail();
        User user = email == null ? null : userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            getRedirectStrategy().sendRedirect(request, response, failureRedirect());
            return;
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        String target = ssoProperties.getSuccessRedirectUrl()
                + "#token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private String failureRedirect() {
        return ssoProperties.getFailureRedirectUrl() + "?error=sso";
    }
}
