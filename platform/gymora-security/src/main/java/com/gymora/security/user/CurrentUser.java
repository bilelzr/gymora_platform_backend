package com.gymora.security.user;

import com.gymora.common.tenant.TenantContext;
import com.gymora.security.GymoraRoles;
import com.gymora.security.jwt.TokenClaims;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The signed-in user, as seen by GYMORA. */
public record CurrentUser(
        String userId,
        String username,
        String email,
        String fullName,
        UUID tenantId,
        Set<String> roles,
        Set<String> capabilities) {

    public boolean isPlatformAdmin() {
        return roles.contains(GymoraRoles.PLATFORM_ADMIN);
    }

    public boolean isGymOwner() {
        return roles.contains(GymoraRoles.GYM_OWNER);
    }

    /** The current user, if the request carries a valid token. */
    public static Optional<CurrentUser> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            return Optional.empty();
        }
        Jwt jwt = token.getToken();
        Set<String> capabilities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(name -> name.startsWith(GymoraRoles.CAPABILITY_PREFIX))
                .map(name -> name.substring(GymoraRoles.CAPABILITY_PREFIX.length()))
                .collect(Collectors.toUnmodifiableSet());
        return Optional.of(new CurrentUser(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                TenantContext.current().orElse(null),
                TokenClaims.roles(jwt),
                capabilities));
    }
}
