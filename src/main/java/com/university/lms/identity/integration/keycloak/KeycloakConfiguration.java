package com.university.lms.identity.integration.keycloak;

import com.university.lms.identity.spi.IdentityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the identity-provider adapter, or a clearly unavailable stand-in.
 *
 * <p>The choice is made once, at start-up, from configuration — not per call. An environment either
 * has administrative credentials or it does not, and discovering that halfway through an operation
 * would leave the local database and the provider disagreeing.
 */
@Configuration
class KeycloakConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KeycloakConfiguration.class);

    @Bean
    IdentityProvider identityProvider(KeycloakAdminProperties properties) {
        if (!properties.enabled()) {
            log.warn(
                    "Identity-provider administration is DISABLED. Token validation is unaffected, but "
                            + "account and role operations will be refused. Set lms.identity.keycloak.enabled=true "
                            + "with service-account credentials to enable them.");
            return new UnavailableIdentityProvider(accountUriFallback(properties));
        }

        // Timeouts are not optional. A hung call to the identity provider inside a request thread
        // holds that thread, and enough of them stop the application serving anything at all.
        RestClient restClient = RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(properties.connectTimeout())
                        .withReadTimeout(properties.readTimeout())))
                .build();

        log.info("Identity-provider administration enabled against realm {}", properties.realm());
        return new KeycloakIdentityProvider(
                restClient, properties, new KeycloakServiceAccountTokens(restClient, properties));
    }

    /** Derivable without credentials, so an unconfigured environment can still link to the account page. */
    private static String accountUriFallback(KeycloakAdminProperties properties) {
        if (properties.baseUrl() == null || properties.realm() == null) {
            return "about:blank";
        }
        return properties.accountUri();
    }
}
