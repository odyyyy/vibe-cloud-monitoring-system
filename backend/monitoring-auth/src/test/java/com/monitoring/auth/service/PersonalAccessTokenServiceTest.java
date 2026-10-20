package com.monitoring.auth.service;

import com.monitoring.auth.dto.pat.PatSummary;
import com.monitoring.auth.entity.PersonalAccessToken;
import com.monitoring.auth.exception.InvalidPatScopeException;
import com.monitoring.auth.exception.PatNotFoundException;
import com.monitoring.auth.repository.PersonalAccessTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PersonalAccessTokenServiceTest {

    private static final long USER_ID = 7L;
    private static final long ORG_ID = 10L;
    private static final long TOKEN_ID = 42L;

    @Mock
    private PersonalAccessTokenRepository tokenRepository;

    private PersonalAccessTokenService service;

    @BeforeEach
    void setUp() {
        service = new PersonalAccessTokenService(tokenRepository);
    }

    @Test
    void generateTokenStoresOnlyTheHashAndReturnsTheRawValue() {
        given(tokenRepository.save(any(PersonalAccessToken.class)))
                .willAnswer(invocation -> {
                    PersonalAccessToken token = invocation.getArgument(0);
                    token.setId(TOKEN_ID);
                    return token;
                });

        PersonalAccessTokenService.GeneratedToken generated = service.generateToken(
                USER_ID, ORG_ID, "  ci token  ", Set.of("read:metrics"), 30);

        PersonalAccessToken token = generated.token();
        assertThat(generated.rawToken()).startsWith("pat_");
        assertThat(generated.rawToken()).hasSizeGreaterThan(40);
        assertThat(token.getTokenHash()).hasSize(64).isNotEqualTo(generated.rawToken());
        assertThat(token.getTokenHash()).isEqualTo(PersonalAccessTokenService.hashToken(generated.rawToken()));
        assertThat(token.getName()).isEqualTo("ci token");
        assertThat(token.getUserId()).isEqualTo(USER_ID);
        assertThat(token.getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(token.getScopes()).containsExactly("read:metrics");
        assertThat(token.getExpiresAt()).isAfter(Instant.now().plus(29, ChronoUnit.DAYS));
    }

    @Test
    void generateTokenRejectsEmptyScopes() {
        assertThatThrownBy(() -> service.generateToken(USER_ID, ORG_ID, "name", Set.of(), 30))
                .isInstanceOf(InvalidPatScopeException.class);
        then(tokenRepository).should(never()).save(any());
    }

    @Test
    void generateTokenRejectsUnknownScopes() {
        assertThatThrownBy(() -> service.generateToken(USER_ID, ORG_ID, "name", Set.of("read:everything"), 30))
                .isInstanceOf(InvalidPatScopeException.class)
                .hasMessageContaining("read:everything");
    }

    @Test
    void authenticateReturnsIdentityForAValidToken() {
        String raw = "pat_valid";
        PersonalAccessToken token = token(raw, Instant.now().plus(1, ChronoUnit.DAYS));
        given(tokenRepository.findByTokenHash(PersonalAccessTokenService.hashToken(raw)))
                .willReturn(Optional.of(token));

        Optional<PatAuthResult> result = service.authenticate(raw);

        assertThat(result).isPresent();
        assertThat(result.get().tokenId()).isEqualTo(TOKEN_ID);
        assertThat(result.get().userId()).isEqualTo(USER_ID);
        assertThat(result.get().organizationId()).isEqualTo(ORG_ID);
        assertThat(result.get().scopes()).containsExactly("read:metrics");
    }

    @Test
    void authenticateRejectsExpiredTokens() {
        String raw = "pat_expired";
        PersonalAccessToken token = token(raw, Instant.now().minus(1, ChronoUnit.DAYS));
        given(tokenRepository.findByTokenHash(PersonalAccessTokenService.hashToken(raw)))
                .willReturn(Optional.of(token));

        assertThat(service.authenticate(raw)).isEmpty();
    }

    @Test
    void authenticateRejectsUnknownAndMalformedTokens() {
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("app_something")).isEmpty();
        given(tokenRepository.findByTokenHash(any())).willReturn(Optional.empty());
        assertThat(service.authenticate("pat_unknown")).isEmpty();
    }

    @Test
    void revokeTokenScopesToOwnerAndOrganization() {
        given(tokenRepository.findByIdAndUserIdAndOrganizationId(TOKEN_ID, USER_ID, ORG_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeToken(TOKEN_ID, USER_ID, ORG_ID))
                .isInstanceOf(PatNotFoundException.class);
        then(tokenRepository).should(never()).delete(any());
    }

    @Test
    void revokeTokenDeletesAnOwnedToken() {
        PersonalAccessToken token = token("pat_x", Instant.now().plus(1, ChronoUnit.DAYS));
        given(tokenRepository.findByIdAndUserIdAndOrganizationId(TOKEN_ID, USER_ID, ORG_ID))
                .willReturn(Optional.of(token));

        service.revokeToken(TOKEN_ID, USER_ID, ORG_ID);

        then(tokenRepository).should().delete(token);
    }

    @Test
    void listActiveTokensMapsEntitiesToSummaries() {
        PersonalAccessToken token = token("pat_x", Instant.now().plus(1, ChronoUnit.DAYS));
        token.setLastUsedAt(Instant.parse("2026-10-01T00:00:00Z"));
        given(tokenRepository.findByUserIdAndOrganizationIdAndExpiresAtAfterOrderByCreatedAtDesc(
                eq(USER_ID), eq(ORG_ID), any(Instant.class)))
                .willReturn(List.of(token));

        List<PatSummary> summaries = service.listActiveTokens(USER_ID, ORG_ID);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).id()).isEqualTo(TOKEN_ID);
        assertThat(summaries.get(0).name()).isEqualTo("token");
        assertThat(summaries.get(0).scopes()).containsExactly("read:metrics");
        assertThat(summaries.get(0).lastUsedAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    void markLastUsedUpdatesTheEntity() {
        PersonalAccessToken token = token("pat_x", Instant.now().plus(1, ChronoUnit.DAYS));
        given(tokenRepository.findById(TOKEN_ID)).willReturn(Optional.of(token));

        service.markLastUsedAsync(TOKEN_ID);

        assertThat(token.getLastUsedAt()).isNotNull();
        then(tokenRepository).should().save(token);
        then(tokenRepository).should(never()).findByIdAndUserIdAndOrganizationId(anyLong(), anyLong(), anyLong());
    }

    @Test
    void markLastUsedIgnoresMissingTokens() {
        given(tokenRepository.findById(TOKEN_ID)).willReturn(Optional.empty());
        service.markLastUsedAsync(TOKEN_ID);
        then(tokenRepository).should(never()).save(any());
    }

    private PersonalAccessToken token(String raw, Instant expiresAt) {
        return PersonalAccessToken.builder()
                .id(TOKEN_ID)
                .userId(USER_ID)
                .organizationId(ORG_ID)
                .name("token")
                .tokenHash(PersonalAccessTokenService.hashToken(raw))
                .expiresAt(expiresAt)
                .scopes(Set.of("read:metrics"))
                .build();
    }
}
