package com.monitoring.auth.service;

import com.monitoring.auth.config.SsoProperties;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.exception.SsoAuthenticationException;
import com.monitoring.auth.repository.UserRepository;
import com.monitoring.organization.entity.MembershipRole;
import com.monitoring.organization.service.OrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SsoUserProvisioningServiceTest {

    private static final String EMAIL = "jane.doe@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OrganizationService organizationService;

    private SsoProperties ssoProperties;
    private SsoUserProvisioningService service;

    @BeforeEach
    void setUp() {
        ssoProperties = new SsoProperties();
        ssoProperties.setRoleClaim("groups");
        ssoProperties.setRoleMapping(Map.of("platform-owners", MembershipRole.OWNER,
                "platform-admins", MembershipRole.ADMIN));
        ssoProperties.setDefaultRole(MembershipRole.VIEWER);
        service = new SsoUserProvisioningService(userRepository, passwordEncoder, organizationService, ssoProperties);
    }

    @Test
    void provisionCreatesVerifiedUserAndAppliesMappedRole() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
        given(passwordEncoder.encode(anyString())).willReturn("random-hash");
        given(userRepository.existsByUsername(anyString())).willReturn(false);
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });

        User provisioned = service.provision(EMAIL, "Jane Doe", List.of("platform-owners"));

        assertThat(provisioned.getId()).isEqualTo(42L);
        assertThat(provisioned.getEmail()).isEqualTo(EMAIL);
        assertThat(provisioned.getUsername()).isEqualTo("JaneDoe");
        assertThat(provisioned.getPassword()).isEqualTo("random-hash");
        assertThat(provisioned.isEmailVerified()).isTrue();
        assertThat(provisioned.getEmailVerifiedAt()).isNotNull();

        then(organizationService).should()
                .ensurePersonalMembership(42L, "JaneDoe", MembershipRole.OWNER);
    }

    @Test
    void provisionReusesExistingUserAndDefaultsToViewer() {
        User existing = User.builder()
                .id(7L)
                .username("jane")
                .email(EMAIL)
                .password("stored-hash")
                .emailVerified(true)
                .build();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(existing));

        User provisioned = service.provision(EMAIL, null, List.of("unmapped-group"));

        assertThat(provisioned).isSameAs(existing);
        then(userRepository).should(never()).save(any(User.class));
        then(organizationService).should()
                .ensurePersonalMembership(7L, "jane", MembershipRole.VIEWER);
    }

    @Test
    void provisionMarksUnverifiedUserAsVerified() {
        User existing = User.builder()
                .id(7L)
                .username("jane")
                .email(EMAIL)
                .password("stored-hash")
                .emailVerified(false)
                .build();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(existing));
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.provision(EMAIL, null, List.of());

        assertThat(existing.isEmailVerified()).isTrue();
        assertThat(existing.getEmailVerifiedAt()).isNotNull();
    }

    @Test
    void provisionRejectsIdentityWithoutEmail() {
        assertThatThrownBy(() -> service.provision("  ", null, List.of()))
                .isInstanceOf(SsoAuthenticationException.class);

        then(userRepository).should(never()).save(any(User.class));
        then(organizationService).shouldHaveNoInteractions();
    }

    @Test
    void provisionAppendsSuffixWhenUsernameIsTaken() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
        given(passwordEncoder.encode(anyString())).willReturn("random-hash");
        given(userRepository.existsByUsername("JaneDoe")).willReturn(true);
        given(userRepository.existsByUsername("JaneDoe1")).willReturn(false);
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });

        User provisioned = service.provision(EMAIL, "Jane Doe", List.of());

        assertThat(provisioned.getUsername()).isEqualTo("JaneDoe1");
    }

    @Test
    void resolveRolePrefersMostPrivilegedMatch() {
        MembershipRole role = service.resolveRole(List.of("platform-admins", "platform-owners"));

        assertThat(role).isEqualTo(MembershipRole.OWNER);
    }

    @Test
    void resolveRoleFallsBackToDefaultWhenNoGroupMatches() {
        assertThat(service.resolveRole(List.of("nobody"))).isEqualTo(MembershipRole.VIEWER);
        assertThat(service.resolveRole(null)).isEqualTo(MembershipRole.VIEWER);
    }

    @Test
    void resolveRoleHonoursConfiguredDefault() {
        ssoProperties.setDefaultRole(MembershipRole.ADMIN);

        assertThat(service.resolveRole(List.of("nobody"))).isEqualTo(MembershipRole.ADMIN);
    }

    @Test
    void provisionUsesEmailLocalPartWhenNameIsMissing() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
        given(passwordEncoder.encode(anyString())).willReturn("random-hash");
        given(userRepository.existsByUsername(anyString())).willReturn(false);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        given(userRepository.save(captor.capture())).willAnswer(invocation -> invocation.getArgument(0));

        service.provision(EMAIL, "  ", List.of());

        assertThat(captor.getValue().getUsername()).isEqualTo("jane.doe");
    }
}
