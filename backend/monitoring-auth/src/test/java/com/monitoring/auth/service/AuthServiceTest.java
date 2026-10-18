package com.monitoring.auth.service;

import com.monitoring.auth.config.AuthProperties;
import com.monitoring.auth.dto.response.AuthResponse;
import com.monitoring.auth.dto.request.LoginRequest;
import com.monitoring.auth.dto.response.MessageResponse;
import com.monitoring.auth.dto.request.RegisterRequest;
import com.monitoring.auth.dto.response.UserProfileResponse;
import com.monitoring.auth.entity.AuthToken;
import com.monitoring.auth.entity.AuthTokenType;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.exception.EmailAlreadyUsedException;
import com.monitoring.auth.exception.EmailNotVerifiedException;
import com.monitoring.auth.exception.InvalidCredentialsException;
import com.monitoring.auth.exception.InvalidTokenException;
import com.monitoring.auth.jwt.JwtTokenProvider;
import com.monitoring.auth.organization.service.OrganizationService;
import com.monitoring.auth.repository.AuthTokenRepository;
import com.monitoring.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthTokenRepository authTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private EmailSender emailSender;

    @Mock
    private OrganizationService organizationService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        AuthProperties authProperties = new AuthProperties();
        authService = new AuthService(
                userRepository, authTokenRepository, passwordEncoder, jwtTokenProvider, emailSender,
                authProperties, organizationService);
    }

    // --- profile ---

    @Test
    void getProfile_returnsUserProfile() {
        User user = User.builder()
                .id(7L)
                .username("ada")
                .email("ada@example.com")
                .avatarUrl("https://cdn.example.com/ada.png")
                .build();
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        UserProfileResponse profile = authService.getProfile(7L);

        assertThat(profile.getId()).isEqualTo(7L);
        assertThat(profile.getUsername()).isEqualTo("ada");
        assertThat(profile.getEmail()).isEqualTo("ada@example.com");
        assertThat(profile.getAvatarUrl()).isEqualTo("https://cdn.example.com/ada.png");
    }

    @Test
    void getProfile_returnsNullAvatarWhenNotSet() {
        User user = User.builder()
                .id(8L)
                .username("grace")
                .email("grace@example.com")
                .build();
        given(userRepository.findById(8L)).willReturn(Optional.of(user));

        UserProfileResponse profile = authService.getProfile(8L);

        assertThat(profile.getAvatarUrl()).isNull();
    }

    @Test
    void getProfile_throwsWhenUserMissing() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getProfile(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");
    }

    // --- register ---

    @Test
    void register_createsUnverifiedUserAndSendsConfirmationEmail() {
        given(userRepository.existsByUsername("ada")).willReturn(false);
        given(userRepository.existsByEmail("ada@example.com")).willReturn(false);
        given(passwordEncoder.encode("supersecret")).willReturn("hashed");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        MessageResponse response = authService.register(registerRequest());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(userCaptor.capture());
        assertThat(userCaptor.getValue().isEmailVerified()).isFalse();
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("hashed");

        ArgumentCaptor<AuthToken> tokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        then(authTokenRepository).should().save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getType()).isEqualTo(AuthTokenType.EMAIL_VERIFICATION);
        assertThat(tokenCaptor.getValue().getUserId()).isEqualTo(1L);
        assertThat(tokenCaptor.getValue().getTokenHash()).hasSize(64);
        assertThat(tokenCaptor.getValue().getExpiresAt()).isAfter(Instant.now());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        then(emailSender).should().sendEmailVerification(eq("ada@example.com"), eq("ada"), linkCaptor.capture());
        assertThat(linkCaptor.getValue()).startsWith("http://localhost:3000/verify-email?token=");

        assertThat(response.getMessage()).isNotBlank();
    }

    @Test
    void register_rejectsDuplicateUsername() {
        given(userRepository.existsByUsername("ada")).willReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(EmailAlreadyUsedException.class);

        then(userRepository).should(never()).save(any(User.class));
    }

    @Test
    void register_rejectsDuplicateEmail() {
        given(userRepository.existsByUsername("ada")).willReturn(false);
        given(userRepository.existsByEmail("ada@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(EmailAlreadyUsedException.class);

        then(userRepository).should(never()).save(any(User.class));
    }

    // --- login ---

    @Test
    void login_returnsTokenWhenVerified() {
        User user = User.builder().id(3L).username("ada").password("hashed").emailVerified(true).build();
        given(userRepository.findByUsername("ada")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("supersecret", "hashed")).willReturn(true);
        given(jwtTokenProvider.generateToken(3L, "ada")).willReturn("jwt-token");

        AuthResponse response = authService.login(loginRequest());

        assertThat(response.getToken()).isEqualTo("jwt-token");
    }

    @Test
    void login_returnsTokenWhenIdentifierIsEmail() {
        User user = User.builder().id(3L).username("ada").password("hashed").emailVerified(true).build();
        given(userRepository.findByEmail("ada@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("supersecret", "hashed")).willReturn(true);
        given(jwtTokenProvider.generateToken(3L, "ada")).willReturn("jwt-token");

        LoginRequest request = loginRequest();
        request.setUsername("ada@example.com");
        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        then(userRepository).should(never()).findByUsername(anyString());
    }

    @Test
    void login_trimsIdentifierBeforeLookup() {
        User user = User.builder().id(3L).username("ada").password("hashed").emailVerified(true).build();
        given(userRepository.findByUsername("ada")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("supersecret", "hashed")).willReturn(true);
        given(jwtTokenProvider.generateToken(3L, "ada")).willReturn("jwt-token");

        LoginRequest request = loginRequest();
        request.setUsername("  ada  ");
        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
    }

    @Test
    void login_rejectsUnknownEmail() {
        given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

        LoginRequest request = loginRequest();
        request.setUsername("nobody@example.com");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_blocksUnverifiedUser() {
        User user = User.builder().id(3L).username("ada").password("hashed").emailVerified(false).build();
        given(userRepository.findByUsername("ada")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("supersecret", "hashed")).willReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void login_rejectsWrongPassword() {
        User user = User.builder().id(3L).username("ada").password("hashed").emailVerified(true).build();
        given(userRepository.findByUsername("ada")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("supersecret", "hashed")).willReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_rejectsUnknownUserWithSameErrorAsWrongPassword() {
        given(userRepository.findByUsername("ada")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // --- verify email ---

    @Test
    void verifyEmail_marksUserVerified() {
        User user = User.builder().id(1L).username("ada").email("ada@example.com").build();
        given(authTokenRepository.findByTokenHashAndType(anyString(), eq(AuthTokenType.EMAIL_VERIFICATION)))
                .willReturn(Optional.of(activeToken(1L, AuthTokenType.EMAIL_VERIFICATION)));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        authService.verifyEmail("raw-token");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        then(userRepository).should().save(user);
    }

    @Test
    void verifyEmail_rejectsExpiredToken() {
        AuthToken expired = activeToken(1L, AuthTokenType.EMAIL_VERIFICATION);
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        given(authTokenRepository.findByTokenHashAndType(anyString(), eq(AuthTokenType.EMAIL_VERIFICATION)))
                .willReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.verifyEmail("raw-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyEmail_rejectsUsedToken() {
        AuthToken used = activeToken(1L, AuthTokenType.EMAIL_VERIFICATION);
        used.setUsedAt(Instant.now());
        given(authTokenRepository.findByTokenHashAndType(anyString(), eq(AuthTokenType.EMAIL_VERIFICATION)))
                .willReturn(Optional.of(used));

        assertThatThrownBy(() -> authService.verifyEmail("raw-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyEmail_rejectsUnknownToken() {
        given(authTokenRepository.findByTokenHashAndType(anyString(), eq(AuthTokenType.EMAIL_VERIFICATION)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("raw-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyEmail_rejectsBlankToken() {
        assertThatThrownBy(() -> authService.verifyEmail(" "))
                .isInstanceOf(InvalidTokenException.class);
    }

    // --- resend verification ---

    @Test
    void resendVerification_isSilentForUnknownEmail() {
        given(userRepository.findByEmail("ada@example.com")).willReturn(Optional.empty());

        MessageResponse response = authService.resendVerification("ada@example.com");

        assertThat(response.getMessage()).isNotBlank();
        then(emailSender).should(never()).sendEmailVerification(anyString(), anyString(), anyString());
    }

    @Test
    void resendVerification_skipsAlreadyVerifiedUsers() {
        User verified = User.builder().id(1L).username("ada").email("ada@example.com").emailVerified(true).build();
        given(userRepository.findByEmail("ada@example.com")).willReturn(Optional.of(verified));

        authService.resendVerification("ada@example.com");

        then(emailSender).should(never()).sendEmailVerification(anyString(), anyString(), anyString());
    }

    // --- forgot password ---

    @Test
    void forgotPassword_sendsResetForExistingUser() {
        User user = User.builder().id(1L).username("ada").email("ada@example.com").build();
        given(userRepository.findByEmail("ada@example.com")).willReturn(Optional.of(user));

        authService.forgotPassword("ada@example.com");

        ArgumentCaptor<AuthToken> tokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        then(authTokenRepository).should().save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getType()).isEqualTo(AuthTokenType.PASSWORD_RESET);

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        then(emailSender).should().sendPasswordReset(eq("ada@example.com"), eq("ada"), linkCaptor.capture());
        assertThat(linkCaptor.getValue()).startsWith("http://localhost:3000/reset-password?token=");
    }

    @Test
    void forgotPassword_isSilentForUnknownEmail() {
        given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

        MessageResponse response = authService.forgotPassword("nobody@example.com");

        assertThat(response.getMessage()).isNotBlank();
        then(emailSender).should(never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    // --- reset password ---

    @Test
    void resetPassword_updatesPasswordAndConsumesToken() {
        User user = User.builder().id(1L).username("ada").email("ada@example.com").password("old").build();
        AuthToken token = activeToken(1L, AuthTokenType.PASSWORD_RESET);
        given(authTokenRepository.findByTokenHashAndType(anyString(), eq(AuthTokenType.PASSWORD_RESET)))
                .willReturn(Optional.of(token));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.encode("newsecret1")).willReturn("new-hash");

        authService.resetPassword("raw-token", "newsecret1");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(token.getUsedAt()).isNotNull();
        then(authTokenRepository).should().save(token);
        then(userRepository).should().save(user);
    }

    @Test
    void resetPassword_rejectsInvalidToken() {
        given(authTokenRepository.findByTokenHashAndType(anyString(), eq(AuthTokenType.PASSWORD_RESET)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("raw-token", "newsecret1"))
                .isInstanceOf(InvalidTokenException.class);
    }

    // --- username change ---

    @Test
    void updateUsername_updatesNameAndReturnsProfile() {
        User user = User.builder()
                .id(7L).username("ada").email("ada@example.com").password("hashed").build();
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("supersecret", "hashed")).willReturn(true);
        given(userRepository.existsByUsername("grace")).willReturn(false);

        UserProfileResponse profile = authService.updateUsername(7L, "supersecret", "grace");

        assertThat(profile.getUsername()).isEqualTo("grace");
        assertThat(user.getUsername()).isEqualTo("grace");
        then(userRepository).should().save(user);
    }

    @Test
    void updateUsername_isNoOpWhenUnchanged() {
        User user = User.builder()
                .id(7L).username("ada").email("ada@example.com").password("hashed").build();
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("supersecret", "hashed")).willReturn(true);

        UserProfileResponse profile = authService.updateUsername(7L, "supersecret", "ada");

        assertThat(profile.getUsername()).isEqualTo("ada");
        then(userRepository).should(never()).save(any(User.class));
    }

    @Test
    void updateUsername_rejectsWrongPassword() {
        User user = User.builder()
                .id(7L).username("ada").password("hashed").build();
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

        assertThatThrownBy(() -> authService.updateUsername(7L, "wrong", "grace"))
                .isInstanceOf(InvalidCredentialsException.class);
        then(userRepository).should(never()).save(any(User.class));
    }

    @Test
    void updateUsername_rejectsTakenUsername() {
        User user = User.builder()
                .id(7L).username("ada").password("hashed").build();
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("supersecret", "hashed")).willReturn(true);
        given(userRepository.existsByUsername("grace")).willReturn(true);

        assertThatThrownBy(() -> authService.updateUsername(7L, "supersecret", "grace"))
                .isInstanceOf(EmailAlreadyUsedException.class);
        then(userRepository).should(never()).save(any(User.class));
    }

    @Test
    void isUsernameAvailable_reflectsRepository() {
        given(userRepository.existsByUsername("grace")).willReturn(false);
        given(userRepository.existsByUsername("ada")).willReturn(true);

        assertThat(authService.isUsernameAvailable("grace")).isTrue();
        assertThat(authService.isUsernameAvailable("ada")).isFalse();
        assertThat(authService.isUsernameAvailable("  ")).isFalse();
    }

    // --- email change ---

    @Test
    void requestEmailChange_issuesTokenAndEmailsBothAddresses() {
        User user = User.builder()
                .id(7L).username("ada").email("ada@example.com").password("hashed").build();
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("supersecret", "hashed")).willReturn(true);
        given(userRepository.existsByEmail("new@example.com")).willReturn(false);

        MessageResponse response = authService.requestEmailChange(7L, "supersecret", "new@example.com");

        ArgumentCaptor<AuthToken> tokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        then(authTokenRepository).should().save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getType()).isEqualTo(AuthTokenType.EMAIL_CHANGE);
        assertThat(tokenCaptor.getValue().getNewEmail()).isEqualTo("new@example.com");
        then(authTokenRepository).should().deleteByUserIdAndType(7L, AuthTokenType.EMAIL_CHANGE);

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        then(emailSender).should()
                .sendEmailChangeConfirmation(eq("new@example.com"), eq("ada"), linkCaptor.capture());
        assertThat(linkCaptor.getValue()).startsWith("http://localhost:3000/confirm-email-change?token=");
        then(emailSender).should()
                .sendEmailChangeNotice(eq("ada@example.com"), eq("ada"), eq("new@example.com"));
        assertThat(response.getMessage()).contains("new@example.com");
    }

    @Test
    void requestEmailChange_rejectsWrongPassword() {
        User user = User.builder()
                .id(7L).username("ada").email("ada@example.com").password("hashed").build();
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

        assertThatThrownBy(() -> authService.requestEmailChange(7L, "wrong", "new@example.com"))
                .isInstanceOf(InvalidCredentialsException.class);
        then(authTokenRepository).should(never()).save(any(AuthToken.class));
    }

    @Test
    void requestEmailChange_rejectsEmailUsedByAnotherAccount() {
        User user = User.builder()
                .id(7L).username("ada").email("ada@example.com").password("hashed").build();
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("supersecret", "hashed")).willReturn(true);
        given(userRepository.existsByEmail("taken@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.requestEmailChange(7L, "supersecret", "taken@example.com"))
                .isInstanceOf(EmailAlreadyUsedException.class);
        then(emailSender).should(never())
                .sendEmailChangeConfirmation(anyString(), anyString(), anyString());
    }

    @Test
    void requestEmailChange_rejectsCurrentEmail() {
        User user = User.builder()
                .id(7L).username("ada").email("ada@example.com").password("hashed").build();
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("supersecret", "hashed")).willReturn(true);

        assertThatThrownBy(() -> authService.requestEmailChange(7L, "supersecret", "ada@example.com"))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void confirmEmailChange_updatesEmailAndMarksVerified() {
        User user = User.builder().id(1L).username("ada").email("ada@example.com").build();
        AuthToken token = activeToken(1L, AuthTokenType.EMAIL_CHANGE);
        token.setNewEmail("new@example.com");
        given(authTokenRepository.findByTokenHashAndType(anyString(), eq(AuthTokenType.EMAIL_CHANGE)))
                .willReturn(Optional.of(token));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.existsByEmail("new@example.com")).willReturn(false);

        authService.confirmEmailChange("raw-token");

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(token.getUsedAt()).isNotNull();
        then(userRepository).should().save(user);
    }

    @Test
    void confirmEmailChange_rejectsInvalidToken() {
        given(authTokenRepository.findByTokenHashAndType(anyString(), eq(AuthTokenType.EMAIL_CHANGE)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.confirmEmailChange("raw-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void confirmEmailChange_rejectsAddressClaimedInTheMeantime() {
        User user = User.builder().id(1L).username("ada").email("ada@example.com").build();
        AuthToken token = activeToken(1L, AuthTokenType.EMAIL_CHANGE);
        token.setNewEmail("taken@example.com");
        given(authTokenRepository.findByTokenHashAndType(anyString(), eq(AuthTokenType.EMAIL_CHANGE)))
                .willReturn(Optional.of(token));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.existsByEmail("taken@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.confirmEmailChange("raw-token"))
                .isInstanceOf(EmailAlreadyUsedException.class);
        assertThat(user.getEmail()).isEqualTo("ada@example.com");
    }

    private AuthToken activeToken(Long userId, AuthTokenType type) {
        return AuthToken.builder()
                .id(10L)
                .userId(userId)
                .tokenHash("hash")
                .type(type)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("ada");
        request.setEmail("ada@example.com");
        request.setPassword("supersecret");
        return request;
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("ada");
        request.setPassword("supersecret");
        return request;
    }
}
