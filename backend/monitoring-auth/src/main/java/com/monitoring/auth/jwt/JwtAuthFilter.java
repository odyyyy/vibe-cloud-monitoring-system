package com.monitoring.auth.jwt;

import com.monitoring.auth.entity.Role;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.auth.security.PlatformAdminAccess;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Populates the security context from a {@code Bearer} JWT.
 *
 * <p>An absent, malformed or invalid token simply leaves the request anonymous; authorization
 * rules decide whether the endpoint requires authentication. The principal is the user id as a
 * string, matching what the controllers expect.
 *
 * <p>Human sessions also receive {@code ROLE_PLATFORM_ADMIN} when the account's platform role is
 * {@link Role#PLATFORM_ADMIN}. The role is read from the database on each request so revoking it
 * takes effect immediately instead of waiting for the current token to expire.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            jwtTokenProvider.parseUserId(token).ifPresent(userId -> {
                List<SimpleGrantedAuthority> authorities = userRepository.findById(userId)
                        .filter(user -> user.getRole() == Role.PLATFORM_ADMIN)
                        .map(user -> List.of(new SimpleGrantedAuthority(PlatformAdminAccess.AUTHORITY)))
                        .orElse(List.of());
                var authentication = new UsernamePasswordAuthenticationToken(
                        userId.toString(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }
}
