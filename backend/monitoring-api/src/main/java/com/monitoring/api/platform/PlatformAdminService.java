package com.monitoring.api.platform;

import com.monitoring.auth.entity.Role;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grants the platform-administrator role to a single configured account.
 *
 * <p>The role is a platform-wide privilege, so it is never assignable through the
 * organization member management API: an organization owner must not be able to escalate
 * somebody (or themselves) to platform administrator. The operator opts in by setting
 * {@code monitoring.platform-admin.username}; the promotion is idempotent and is applied on
 * startup and periodically afterwards, so an account that is created later is picked up as
 * well.
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminService.class);

    private final UserRepository userRepository;

    /** Username of the account that receives the platform administrator role (opt-in). */
    @Value("${monitoring.platform-admin.username:}")
    private String platformAdminUsername;

    /** Promotes the configured account. */
    @Transactional
    public void ensurePlatformAdmin() {
        ensurePlatformAdmin(platformAdminUsername);
    }

    /**
     * Re-applies the promotion periodically: the configured account may be registered after
     * the application has started.
     */
    @Scheduled(
            fixedDelayString = "${monitoring.platform-admin.sync-interval-ms:300000}",
            initialDelayString = "${monitoring.platform-admin.sync-initial-delay-ms:60000}")
    public void syncPlatformAdmin() {
        ensurePlatformAdmin();
    }

    /**
     * Promotes the account with the given username to {@link Role#PLATFORM_ADMIN}. A blank
     * username disables the promotion entirely, so a default installation has no platform
     * administrator.
     */
    @Transactional
    public void ensurePlatformAdmin(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        String candidate = username.trim();
        User user = userRepository.findByUsername(candidate).orElse(null);
        if (user == null) {
            log.warn("Configured platform administrator '{}' does not exist yet; skip role promotion",
                    candidate);
            return;
        }

        if (user.getRole() == Role.PLATFORM_ADMIN) {
            return;
        }

        user.setRole(Role.PLATFORM_ADMIN);
        userRepository.save(user);
        log.info("Granted platform administrator role to user '{}'", candidate);
    }
}
