package com.gymora.security.jwt;

import com.gymora.security.GymoraRoles;
import com.gymora.security.capability.CapabilityProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Builds a user's authorities: their GYMORA role from Keycloak, plus, for staff (no role), the
 * capabilities granted by their profiles in the application.
 */
@Component
public class GymoraAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final ObjectProvider<CapabilityProvider> capabilityProvider;

    public GymoraAuthoritiesConverter(ObjectProvider<CapabilityProvider> capabilityProvider) {
        this.capabilityProvider = capabilityProvider;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> roles = TokenClaims.roles(jwt);
        List<GrantedAuthority> authorities = new ArrayList<>();
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(GymoraRoles.ROLE_PREFIX + role)));

        Optional<UUID> tenantId = TokenClaims.tenantId(jwt);
        if (roles.isEmpty() && tenantId.isPresent()) {
            CapabilityProvider provider = capabilityProvider.getIfAvailable();
            if (provider != null) {
                provider.capabilitiesOf(jwt.getSubject(), tenantId.get()).forEach(capability ->
                        authorities.add(new SimpleGrantedAuthority(GymoraRoles.CAPABILITY_PREFIX + capability)));
            }
        }
        return authorities;
    }
}
