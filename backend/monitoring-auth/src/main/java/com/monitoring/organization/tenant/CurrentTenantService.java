package com.monitoring.organization.tenant;

import com.monitoring.organization.entity.Membership;
import com.monitoring.organization.exception.TenantAccessDeniedException;
import com.monitoring.organization.repository.MembershipRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * Resolves the tenant (organization) a request acts in.
 *
 * <p>The active organization is selected by the {@code X-Organization-Id} header sent by the
 * frontend switcher. The header is never trusted on its own: the caller must hold a
 * {@link Membership} in that organization. When the header is absent (or the request is not
 * an HTTP request), the caller's first membership becomes the default organization, which keeps
 * non-switching clients and background code working.
 */
@Component
@RequiredArgsConstructor
public class CurrentTenantService {

    public static final String ORGANIZATION_HEADER = "X-Organization-Id";

    private final MembershipRepository membershipRepository;

    /** The authenticated user id from the JWT principal (a string, see {@code JwtAuthFilter}). */
    public Long requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            throw new TenantAccessDeniedException("No authenticated user available");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof String userId) {
            try {
                return Long.parseLong(userId);
            } catch (NumberFormatException ex) {
                throw new TenantAccessDeniedException("Invalid user identity");
            }
        }
        throw new TenantAccessDeniedException("No authenticated user available");
    }

    /**
     * Resolves the active organization and the caller's role in it. A requested organization
     * the caller does not belong to is rejected with 403 rather than silently ignored.
     */
    public TenantContext requireTenant() {
        Long userId = requireUserId();
        Long requestedOrganizationId = requestedOrganizationId();

        if (requestedOrganizationId != null) {
            Membership membership = membershipRepository
                    .findByOrganizationIdAndUserId(requestedOrganizationId, userId)
                    .orElseThrow(TenantAccessDeniedException::new);
            return new TenantContext(userId, membership.getOrganizationId(), membership.getRole());
        }

        List<Membership> memberships = membershipRepository.findByUserIdOrderByIdAsc(userId);
        if (memberships.isEmpty()) {
            throw new TenantAccessDeniedException("You do not belong to any organization");
        }
        Membership membership = memberships.get(0);
        return new TenantContext(userId, membership.getOrganizationId(), membership.getRole());
    }

    public Long requireOrganizationId() {
        return requireTenant().organizationId();
    }

    private Long requestedOrganizationId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        String header = request.getHeader(ORGANIZATION_HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException ex) {
            throw new TenantAccessDeniedException("Invalid organization identifier");
        }
    }
}
