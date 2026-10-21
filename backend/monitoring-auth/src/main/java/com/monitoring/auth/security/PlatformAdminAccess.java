package com.monitoring.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Answers whether the current request was authenticated as a platform administrator.
 *
 * <p>The authority is added only by {@code JwtAuthFilter} for human sessions whose account has
 * {@code Role.PLATFORM_ADMIN}. Personal Access Tokens deliberately never receive it.
 */
@Component
public class PlatformAdminAccess {

    public static final String AUTHORITY = "ROLE_PLATFORM_ADMIN";

    public boolean isPlatformAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(AUTHORITY::equals);
    }
}
