package com.monitoring.auth.service;

import com.monitoring.auth.config.AuthProperties;
import com.monitoring.auth.dto.*;
import com.monitoring.auth.entity.AuthToken;
import com.monitoring.auth.entity.AuthTokenType;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.exception.EmailAlreadyUsedException;
import com.monitoring.auth.exception.EmailNotVerifiedException;
import com.monitoring.auth.exception.InvalidCredentialsException;
import com.monitoring.auth.exception.InvalidTokenException;
import com.monitoring.auth.exception.UserNotFoundException;
import com.monitoring.auth.jwt.JwtTokenProvider;
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
import java.time.Instant;
import java.util.HexFormat;

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
        userRepository.save(user);

        sendVerificationEmail(user);

        return new MessageResponse("Account created. Check your email to confirm your address before signing in.");
    }

    public AuthResponse login(LoginRequest request) {
        // An unknown username and a wrong password produce the same error so the response does
        // not reveal whether an account exists. The verified check runs only after the password
        // is confirmed, so verification status is not leaked to an unauthenticated caller.
        User user = userRepository.findByUsername(request.getUsername())
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

    @Transactional
    public MessageResponse verifyEmail(String rawToken) {
        User user = consumeToken(rawToken, AuthTokenType.EMAIL_VERIFICATION);
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
            String rawToken = issueToken(user.getId(), AuthTokenType.PASSWORD_RESET);
            String link = buildLink(authProperties.getResetUrl(), rawToken);
            emailSender.sendPasswordReset(user.getEmail(), user.getUsername(), link);
        });
        return new MessageResponse("If an account exists for that email, we sent a password reset link.");
    }

    @Transactional
    public MessageResponse resetPassword(String rawToken, String newPassword) {
        User user = consumeToken(rawToken, AuthTokenType.PASSWORD_RESET);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return new MessageResponse("Password updated. You can now sign in with your new password.");
    }

    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getAvatarUrl());
    }

    private void sendVerificationEmail(User user) {
        String rawToken = issueToken(user.getId(), AuthTokenType.EMAIL_VERIFICATION);
        String link = buildLink(authProperties.getVerificationUrl(), rawToken);
        emailSender.sendEmailVerification(user.getEmail(), user.getUsername(), link);
    }

    /**
     * Issues a fresh single-use token, invalidating any previous token of the same type so a
     * user only ever has one active link per purpose. Returns the raw token (stored only as a hash).
     */
    private String issueToken(Long userId, AuthTokenType type) {
        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        String rawToken = HexFormat.of().formatHex(random);

        Instant expiresAt = Instant.now().plus(
                type == AuthTokenType.EMAIL_VERIFICATION
                        ? authProperties.getVerificationTtl()
                        : authProperties.getResetTtl());

        authTokenRepository.deleteByUserIdAndType(userId, type);
        authTokenRepository.save(AuthToken.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .type(type)
                .expiresAt(expiresAt)
                .build());

        return rawToken;
    }

    /** Resolves and marks the token used, returning its owner. Fails closed on any problem. */
    private User consumeToken(String rawToken, AuthTokenType type) {
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

        return userRepository.findById(token.getUserId())
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
