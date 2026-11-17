package com.gymora.app.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.gymora.security.jwt.GymoraAuthoritiesConverter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** Builds Keycloak-shaped test tokens, authorized through GYMORA's real authorities converter. */
public final class Tokens {

    public static final UUID GYM_A = UUID.fromString("a1a1a1a1-0000-4000-8000-00000000000a");
    public static final UUID GYM_B = UUID.fromString("b2b2b2b2-0000-4000-8000-00000000000b");
    /** Suspended in the test data. */
    public static final UUID GYM_C = UUID.fromString("c3c3c3c3-0000-4000-8000-00000000000c");

    private Tokens() {
    }

    public static JwtRequestPostProcessor owner(UUID gym, GymoraAuthoritiesConverter converter) {
        return token("owner-" + gym, gym.toString(), List.of("GYM_OWNER"), converter);
    }

    public static JwtRequestPostProcessor staff(String userId, UUID gym, GymoraAuthoritiesConverter converter) {
        return token(userId, gym == null ? null : gym.toString(), List.of(), converter);
    }

    public static JwtRequestPostProcessor platformAdmin(GymoraAuthoritiesConverter converter) {
        return token("platform-admin", null, List.of("PLATFORM_ADMIN"), converter);
    }

    public static JwtRequestPostProcessor token(String userId, String tenantClaim, List<String> roles,
                                                GymoraAuthoritiesConverter converter) {
        return jwt()
                .jwt(builder -> {
                    builder.subject(userId)
                            .claim("preferred_username", userId)
                            .claim("realm_access", Map.of("roles", roles));
                    if (tenantClaim != null) {
                        builder.claim("tenant_id", tenantClaim);
                    }
                })
                .authorities(converter);
    }
}
