package com.gymora.security.identity;

import com.gymora.security.jwt.TokenClaims;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link IdentityAdmin} backed by the Keycloak admin REST API, called with the {@code gymora-api}
 * client's service account (client credentials). Uses plain HTTP rather than the Keycloak admin
 * client library, whose dependencies often clash with Spring Boot's.
 */
@Component
@EnableConfigurationProperties(KeycloakIdentityAdmin.KeycloakAdminProperties.class)
public class KeycloakIdentityAdmin implements IdentityAdmin {

    private static final Logger log = LoggerFactory.getLogger(KeycloakIdentityAdmin.class);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {
    };

    private final KeycloakAdminProperties properties;
    private final RestClient http;
    private volatile CachedToken token;

    public KeycloakIdentityAdmin(KeycloakAdminProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.http = builder.baseUrl(properties.serverUrl()).build();
    }

    @Override
    public String createUser(NewIdentity identity) {
        Map<String, Object> body = Map.of(
                "username", identity.email(),
                "email", identity.email(),
                "firstName", identity.firstName(),
                "lastName", identity.lastName(),
                "enabled", true,
                "emailVerified", false,
                "attributes", Map.of(TokenClaims.TENANT_CLAIM, List.of(identity.tenantId().toString())));
        try {
            URI location = http.post()
                    .uri("/admin/realms/{realm}/users", properties.realm())
                    .headers(h -> h.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation();
            if (location == null) {
                throw new IllegalStateException("Keycloak did not return the new user's location");
            }
            String path = location.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (HttpClientErrorException.Conflict conflict) {
            throw new IdentityConflictException(identity.email());
        } catch (RestClientException | IllegalStateException e) {
            throw new IdentityProviderException("createUser", e);
        }
    }

    @Override
    public void updateNames(String userId, String firstName, String lastName) {
        Map<String, Object> user = getUser(userId);
        user.put("firstName", firstName);
        user.put("lastName", lastName);
        putUser(userId, user, "updateNames");
    }

    @Override
    public void setEnabled(String userId, boolean enabled) {
        Map<String, Object> user = getUser(userId);
        user.put("enabled", enabled);
        putUser(userId, user, "setEnabled");
    }

    @Override
    public void grantRealmRole(String userId, String role) {
        try {
            Map<String, Object> roleRepresentation = http.get()
                    .uri("/admin/realms/{realm}/roles/{role}", properties.realm(), role)
                    .headers(h -> h.setBearerAuth(accessToken()))
                    .retrieve()
                    .body(JSON_MAP);
            http.post()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", properties.realm(), userId)
                    .headers(h -> h.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(roleRepresentation))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new IdentityProviderException("grantRealmRole", e);
        }
    }

    @Override
    public void sendSetupEmail(String userId) {
        try {
            http.put()
                    .uri(builder -> builder.path("/admin/realms/{realm}/users/{id}/execute-actions-email")
                            .queryParam("lifespan", properties.setupLinkLifespan().toSeconds())
                            .build(properties.realm(), userId))
                    .headers(h -> h.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new IdentityProviderException("sendSetupEmail", e);
        }
    }

    @Override
    public void deleteUser(String userId) {
        try {
            http.delete()
                    .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                    .headers(h -> h.setBearerAuth(accessToken()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound alreadyGone) {
            log.debug("Keycloak user {} was already deleted", userId);
        } catch (RestClientException e) {
            throw new IdentityProviderException("deleteUser", e);
        }
    }

    /**
     * Full user representation. Updates send it back whole: a partial update would drop the
     * tenant_id attribute.
     */
    private Map<String, Object> getUser(String userId) {
        try {
            Map<String, Object> user = http.get()
                    .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                    .headers(h -> h.setBearerAuth(accessToken()))
                    .retrieve()
                    .body(JSON_MAP);
            return user == null ? new HashMap<>() : new HashMap<>(user);
        } catch (RestClientException e) {
            throw new IdentityProviderException("getUser", e);
        }
    }

    private void putUser(String userId, Map<String, Object> user, String operation) {
        try {
            http.put()
                    .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                    .headers(h -> h.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new IdentityProviderException(operation, e);
        }
    }

    /** Service-account token, reused until shortly before it expires. */
    private String accessToken() {
        CachedToken current = token;
        if (current != null && current.validUntil().isAfter(Instant.now())) {
            return current.value();
        }
        synchronized (this) {
            if (token != null && token.validUntil().isAfter(Instant.now())) {
                return token.value();
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", properties.clientId());
            form.add("client_secret", properties.clientSecret());
            try {
                Map<String, Object> response = http.post()
                        .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .onStatus(HttpStatus.UNAUTHORIZED::equals, (req, res) -> {
                            throw new IllegalStateException("Keycloak rejected the gymora-api client credentials");
                        })
                        .body(JSON_MAP);
                if (response == null || response.get("access_token") == null) {
                    throw new IllegalStateException("Keycloak returned no access token");
                }
                long expiresIn = ((Number) response.getOrDefault("expires_in", 60)).longValue();
                token = new CachedToken((String) response.get("access_token"),
                        Instant.now().plusSeconds(Math.max(5, expiresIn - 30)));
                return token.value();
            } catch (RestClientException | IllegalStateException e) {
                throw new IdentityProviderException("token", e);
            }
        }
    }

    private record CachedToken(String value, Instant validUntil) {
    }

    @ConfigurationProperties("gymora.keycloak")
    public record KeycloakAdminProperties(
            String serverUrl,
            String realm,
            String clientId,
            String clientSecret,
            Duration setupLinkLifespan) {

        public KeycloakAdminProperties {
            realm = realm == null ? "gymora" : realm;
            setupLinkLifespan = setupLinkLifespan == null ? Duration.ofDays(3) : setupLinkLifespan;
        }
    }
}
