package com.gymora.security.jwt;

import com.gymora.security.GymoraRoles;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads GYMORA's claims from a Keycloak access token. */
public final class TokenClaims {

    public static final String TENANT_CLAIM = "tenant_id";

    private TokenClaims() {
    }

    /** GYMORA roles from {@code realm_access.roles}; any other Keycloak role is ignored. */
    public static Set<String> roles(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> access) || !(access.get("roles") instanceof Collection<?> roles)) {
            return Set.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .filter(GymoraRoles.ALL::contains)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** The gym from the {@code tenant_id} claim; empty when missing or not a valid UUID. */
    public static Optional<UUID> tenantId(Jwt jwt) {
        String value = jwt.getClaimAsString(TENANT_CLAIM);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            UUID tenantId = UUID.fromString(value.trim());
            return tenantId.equals(new UUID(0L, 0L)) ? Optional.empty() : Optional.of(tenantId);
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
