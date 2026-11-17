package com.gymora.security.user;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

/** Fills created_by / updated_by with the Keycloak user id, or "system" outside a request. */
@Component
public class SecurityAuditorAware implements AuditorAware<String> {

    public static final String SYSTEM = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(CurrentUser.get().map(CurrentUser::userId).orElse(SYSTEM));
    }
}
