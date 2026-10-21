package com.monitoring.api.platform;

import com.monitoring.auth.entity.Role;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the opt-in promotion of a configured account to platform administrator.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:platform-admin-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "monitoring.platform-metrics.enabled=false"
})
class PlatformAdminServiceTest {

    @Autowired
    private PlatformAdminService platformAdminService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void configuredUsernameIsPromotedToPlatformAdmin() {
        User user = userRepository.save(User.builder()
                .username("ops")
                .email("ops@example.com")
                .password("encoded-secret")
                .emailVerified(true)
                .role(Role.USER)
                .build());

        platformAdminService.ensurePlatformAdmin("ops");

        User stored = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(Role.PLATFORM_ADMIN, stored.getRole());
    }

    @Test
    void blankUsernameLeavesAccountsUntouched() {
        User user = userRepository.save(User.builder()
                .username("plain")
                .email("plain@example.com")
                .password("encoded-secret")
                .emailVerified(true)
                .role(Role.USER)
                .build());

        platformAdminService.ensurePlatformAdmin("  ");
        platformAdminService.ensurePlatformAdmin(null);

        User stored = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(Role.USER, stored.getRole());
    }

    @Test
    void unknownUsernameDoesNotAffectOtherAccounts() {
        User user = userRepository.save(User.builder()
                .username("other")
                .email("other@example.com")
                .password("encoded-secret")
                .emailVerified(true)
                .role(Role.USER)
                .build());

        platformAdminService.ensurePlatformAdmin("does-not-exist");

        User stored = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(Role.USER, stored.getRole());
    }
}
