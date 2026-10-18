package com.monitoring.auth.service;

import com.monitoring.auth.config.AuthProperties;
import com.monitoring.auth.dto.request.*;
import com.monitoring.auth.dto.response.*;
import com.monitoring.auth.entity.AuthToken;
import com.monitoring.auth.entity.AuthTokenType;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.exception.EmailAlreadyUsedException;
import com.monitoring.auth.exception.EmailNotVerifiedException;
import com.monitoring.auth.exception.InvalidCredentialsException;
import com.monitoring.auth.exception.InvalidTokenException;
import com.monitoring.auth.exception.UserNotFoundException;
import com.monitoring.auth.jwt.JwtTokenProvider;
import com.monitoring.organization.service.OrganizationService;
import com.monitoring.auth.repository.AuthTokenRepository;
import com.monitoring.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailSender emailSender;
    private final AuthProperties authProperties;
    private final OrganizationService organizationService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new EmailAlreadyUsedException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyUsedException("Email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .emailVerified(false)
                .build();
        User savedUser = userRepository.save(user);

        // Every account starts with a personal organization owned by the user, so tenants and
        // data scoping exist from the very first request.
        organizationService.createPersonal(savedUser.getId(), savedUser.getUsername());

        sendVerificationEmail(savedUser);

        return new MessageResponse("Account created. Check your email to confirm your address before signing in.");
    }

    public AuthResponse login(LoginRequest request) {
        // An unknown account and a wrong password produce the same error so the response does
        // not reveal whether an account exists. The verified check runs only after the password
        // is confirmed, so verification status is not leaked to an unauthenticated caller.
        User user = findByIdentifier(request.getUsername())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException("Email not verified");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token);
    }

    /**
     * Resolves a login identifier that is either a username or an email address. Usernames cannot
     * contain '@' (see {@code RegisterRequest}), so the presence of '@' unambiguously marks an email.
     */
    private Optional<User> findByIdentifier(String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        return value.contains("@")
                ? userRepository.findByEmail(value)
                : userRepository.findByUsername(value);
    }

    @Transactional
    public MessageResponse verifyEmail(String rawToken) {
        AuthToken token = consumeToken(rawToken, AuthTokenType.EMAIL_VERIFICATION);
        User user = requireUser(token.getUserId());
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now());
            userRepository.save(user);
        }
        return new MessageResponse("Email confirmed. You can now sign in.");
    }

    public MessageResponse resendVerification(String email) {
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendVerificationEmail);
        return new MessageResponse(
                "If the account exists and is not yet confirmed, we sent a new confirmation email.");
    }

    public MessageResponse forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String rawToken = issueToken(user.getId(), AuthTokenType.PASSWORD_RESET, null);
            String link = buildLink(authProperties.getResetUrl(), rawToken);
            emailSender.sendPasswordReset(user.getEmail(), user.getUsername(), link);
        });
        return new MessageResponse("If an account exists for that email, we sent a password reset link.");
    }

    @Transactional
    public MessageResponse resetPassword(String rawToken, String newPassword) {
        AuthToken token = consumeToken(rawToken, AuthTokenType.PASSWORD_RESET);
        User user = requireUser(token.getUserId());
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return new MessageResponse("Password updated. You can now sign in with your new password.");
    }

    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return toProfile(user);
    }

    /**
     * Changes the login username after re-authenticating with the current password and checking
     * that the new name is free. The change does not invalidate existing sessions: tokens
     * authenticate by user id, not by username.
     *
     * <p>Callers must have already validated the username format via {@code UpdateUsernameRequest}.
     */
    @Transactional
    public UserProfileResponse updateUsername(Long userId, String currentPassword, String newUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String username = newUsername.trim();
        if (username.equals(user.getUsername())) {
            return toProfile(user);
        }
        if (userRepository.existsByUsername(username)) {
            throw new EmailAlreadyUsedException("Username already exists");
        }

        user.setUsername(username);
        userRepository.save(user);
        return toProfile(user);
    }

    /** Availability probe for the settings UI; unsupported/blank names are reported as unavailable. */
    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        String candidate = username == null ? "" : username.trim();
        if (candidate.isEmpty() || candidate.length() > 50) {
            return false;
        }
        return !userRepository.existsByUsername(candidate);
    }

    /**
     * Starts an email change: the current password is re-checked, the target address must be
     * free, and a single-use confirmation link is sent to that address. The account email is
     * only updated once the link is followed ({@link #confirmEmailChange}), so an address the
     * user does not control is never attached to the account.
     */
    @Transactional
    public MessageResponse requestEmailChange(Long userId, String currentPassword, String newEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String email = newEmail.trim();
        if (email.equalsIgnoreCase(user.getEmail())) {
            throw new EmailAlreadyUsedException("This is already your email address");
        }
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException("Email already exists");
        }

        String rawToken = issueToken(user.getId(), AuthTokenType.EMAIL_CHANGE, email);
        String link = buildLink(authProperties.getEmailChangeUrl(), rawToken);
        emailSender.sendEmailChangeConfirmation(email, user.getUsername(), link);
        emailSender.sendEmailChangeNotice(user.getEmail(), user.getUsername(), email);
        return new MessageResponse("Confirmation link sent to " + email + ". Open it to finish the change.");
    }

    /**
     * Applies a pending email change carried by a single-use token. Uniqueness is re-checked at
     * confirmation time, because another account may have claimed the address in the meantime.
     */
    @Transactional
    public MessageResponse confirmEmailChange(String rawToken) {
        AuthToken token = consumeToken(rawToken, AuthTokenType.EMAIL_CHANGE);
        User user = requireUser(token.getUserId());

        String newEmail = token.getNewEmail();
        if (newEmail == null || newEmail.isBlank()) {
            throw new InvalidTokenException("Invalid or expired token");
        }
        if (userRepository.existsByEmail(newEmail) && !newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new EmailAlreadyUsedException("Email already exists");
        }

        user.setEmail(newEmail);
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);
        return new MessageResponse("Your email address has been updated.");
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getAvatarUrl());
    }

    private void sendVerificationEmail(User user) {
        String rawToken = issueToken(user.getId(), AuthTokenType.EMAIL_VERIFICATION, null);
        String link = buildLink(authProperties.getVerificationUrl(), rawToken);
        emailSender.sendEmailVerification(user.getEmail(), user.getUsername(), link);
    }

    /**
     * Issues a fresh single-use token, invalidating any previous token of the same type so a
     * user only ever has one active link per purpose. Returns the raw token (stored only as a
     * hash). {@code newEmail} is only set for {@link AuthTokenType#EMAIL_CHANGE}.
     */
    private String issueToken(Long userId, AuthTokenType type, String newEmail) {
        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        String rawToken = HexFormat.of().formatHex(random);

        Instant expiresAt = Instant.now().plus(ttlFor(type));

        authTokenRepository.deleteByUserIdAndType(userId, type);
        authTokenRepository.save(AuthToken.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .type(type)
                .newEmail(newEmail)
                .expiresAt(expiresAt)
                .build());

        return rawToken;
    }

    private Duration ttlFor(AuthTokenType type) {
        return switch (type) {
            case EMAIL_VERIFICATION -> authProperties.getVerificationTtl();
            case PASSWORD_RESET -> authProperties.getResetTtl();
            case EMAIL_CHANGE -> authProperties.getEmailChangeTtl();
        };
    }

    /**
     * Resolves and marks the token used, returning it. Fails closed on any problem: a blank,
     * unknown, already-used or expired token all raise the same error.
     */
    private AuthToken consumeToken(String rawToken, AuthTokenType type) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException("Invalid or expired token");
        }

        AuthToken token = authTokenRepository.findByTokenHashAndType(hash(rawToken), type)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));

        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(Instant.now())) {
            throw new InvalidTokenException("Invalid or expired token");
        }

        token.setUsedAt(Instant.now());
        authTokenRepository.save(token);

        return token;
    }

    /** Loads the token owner; a missing user is treated like an invalid token, not a 500. */
    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));
    }

    private String buildLink(String baseUrl, String rawToken) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "token=" + rawToken;
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
