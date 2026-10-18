package com.monitoring.collector.security;

import com.monitoring.storage.entity.Application;
import com.monitoring.storage.repository.ApplicationRepository;
import com.monitoring.storage.security.ApiKeyHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ApiKeyAuthFilterTest {

    private ApplicationRepository applicationRepository;
    private ApiKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        applicationRepository = Mockito.mock(ApplicationRepository.class);
        filter = new ApiKeyAuthFilter(applicationRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Application app() {
        return Application.builder().id(7L).name("my-app")
                .apiKeyHash(ApiKeyHasher.hash("app_valid")).organizationId(1L).build();
    }

    @Test
    void validKey_authenticatesAndContinuesChain() throws Exception {
        Application application = app();
        when(applicationRepository.findByApiKeyHash(ApiKeyHasher.hash("app_valid")))
                .thenReturn(Optional.of(application));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "app_valid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertSame(application, authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_AGENT")));
        assertNotNull(chain.getRequest(), "chain must be called for a valid key");
    }

    @Test
    void missingKey_returns401AndStopsChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest(), "chain must not be called");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void invalidKey_returns401AndStopsChain() throws Exception {
        when(applicationRepository.findByApiKeyHash(ApiKeyHasher.hash("app_bad"))).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "app_bad");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest(), "chain must not be called");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
