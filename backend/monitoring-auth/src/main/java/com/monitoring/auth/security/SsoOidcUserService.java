package com.monitoring.auth.security;

import com.monitoring.auth.config.SsoProperties;
import com.monitoring.auth.exception.SsoAuthenticationException;
import com.monitoring.auth.service.SsoUserProvisioningService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * OIDC user service that reconciles the provider identity with a local account.
 *
 * <p>It delegates the actual user-info retrieval to the default implementation and then runs
 * just-in-time provisioning. The IDP tokens are only used to read the identity here; they are
 * never persisted by the application.
 */
@Component
@ConditionalOnProperty(prefix = "app.sso", name = "enabled", havingValue = "true")
public class SsoOidcUserService extends OidcUserService {

    private final SsoUserProvisioningService provisioningService;
    private final SsoProperties ssoProperties;

    public SsoOidcUserService(SsoUserProvisioningService provisioningService, SsoProperties ssoProperties) {
        this.provisioningService = provisioningService;
        this.ssoProperties = ssoProperties;
        setOauth2UserService(new DefaultOAuth2UserService());
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        try {
            provisioningService.provision(
                    oidcUser.getEmail(),
                    oidcUser.getFullName(),
                    oidcUser.getClaimAsStringList(ssoProperties.getRoleClaim()));
        } catch (SsoAuthenticationException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"), ex.getMessage(), ex);
        }
        return oidcUser;
    }
}
