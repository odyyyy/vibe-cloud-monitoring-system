package com.monitoring.auth.jwt;

import com.monitoring.auth.service.PatAuthResult;
import com.monitoring.auth.service.PersonalAccessTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class PatAuthFilterTest {

    private static final String RAW_TOKEN = "pat_test_token";
    private static final long TOKEN_ID = 42L;
    private static final long USER_ID = 7L;
    private static final long ORG_ID = 10L;

    private PersonalAccessTokenService patService;
    private PatAuthFilter filter;

    @BeforeEach
    void setUp() {
        patService = mock(PersonalAccessTokenService.class);
        filter = new PatAuthFilter(patService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesTokenAndPopulatesSecurityContext() throws Exception {
        given(patService.authenticate(RAW_TOKEN)).willReturn(Optional.of(
                result(Set.of("read:metrics"), null)));
        MockHttpServletRequest request = request("GET", "/api/v1/metrics/names");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(Long.toString(USER_ID));
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("SCOPE_read:metrics");
        assertThat(authentication.getDetails()).isInstanceOf(PatAuthenticationDetails.class);
        assertThat(((PatAuthenticationDetails) authentication.getDetails()).organizationId()).isEqualTo(ORG_ID);
        assertThat(chain.getRequest()).isNotNull();
        then(patService).should().markLastUsedAsync(TOKEN_ID);
    }

    @Test
    void rejectsMissingScopeWithForbidden() throws Exception {
        given(patService.authenticate(RAW_TOKEN)).willReturn(Optional.of(
                result(Set.of("read:metrics"), null)));
        MockHttpServletRequest request = request("POST", "/api/v1/dashboards");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void allowsMutationWhenWriteScopeIsPresent() throws Exception {
        given(patService.authenticate(RAW_TOKEN)).willReturn(Optional.of(
                result(Set.of("write:dashboards"), null)));
        MockHttpServletRequest request = request("POST", "/api/v1/dashboards");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void expiredOrRevokedTokenLeavesRequestAnonymous() throws Exception {
        given(patService.authenticate(RAW_TOKEN)).willReturn(Optional.empty());
        MockHttpServletRequest request = request("GET", "/api/v1/metrics/names");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void settingsPathsAreNotAuthenticatedByPat() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/settings/pats");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        then(patService).should(never()).authenticate(anyString());
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void rejectsTokenUsedWithAnotherOrganization() throws Exception {
        given(patService.authenticate(RAW_TOKEN)).willReturn(Optional.of(
                result(Set.of("read:metrics"), null)));
        MockHttpServletRequest request = request("GET", "/api/v1/metrics/names");
        request.addHeader("X-Organization-Id", "999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void allowsTokenWhenOrganizationHeaderMatches() throws Exception {
        given(patService.authenticate(RAW_TOKEN)).willReturn(Optional.of(
                result(Set.of("read:metrics"), null)));
        MockHttpServletRequest request = request("GET", "/api/v1/metrics/names");
        request.addHeader("X-Organization-Id", Long.toString(ORG_ID));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void publicShareRouteNeedsNoScope() throws Exception {
        given(patService.authenticate(RAW_TOKEN)).willReturn(Optional.of(
                result(Set.of("read:metrics"), null)));
        MockHttpServletRequest request = request("GET", "/api/v1/dashboards/share/token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void recentLastUsedIsNotTouchedAgain() throws Exception {
        given(patService.authenticate(RAW_TOKEN)).willReturn(Optional.of(
                result(Set.of("read:metrics"), Instant.now().minus(Duration.ofSeconds(5)))));
        MockHttpServletRequest request = request("GET", "/api/v1/metrics/names");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        then(patService).should(never()).markLastUsedAsync(anyLong());
    }

    @Test
    void nonPatBearerTokenIsIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/metrics/names");
        request.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.jwt");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        then(patService).should(never()).authenticate(anyString());
        assertThat(chain.getRequest()).isNotNull();
    }

    private PatAuthResult result(Set<String> scopes, Instant lastUsedAt) {
        return new PatAuthResult(TOKEN_ID, USER_ID, ORG_ID, scopes, lastUsedAt);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Authorization", "Bearer " + RAW_TOKEN);
        return request;
    }
}
