package com.gymora.security;

import java.util.Set;

/**
 * The only two roles held in Keycloak. Every other permission is a capability granted inside
 * GYMORA through profiles.
 */
public final class GymoraRoles {

    /** Runs the GYMORA platform: manages gyms. Never sees a gym's own data. */
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    /** Owns one gym: holds every capability inside that gym. */
    public static final String GYM_OWNER = "GYM_OWNER";

    public static final Set<String> ALL = Set.of(PLATFORM_ADMIN, GYM_OWNER);

    /** Spring authority prefix for roles, e.g. ROLE_GYM_OWNER. */
    public static final String ROLE_PREFIX = "ROLE_";

    /** Spring authority prefix for capabilities, e.g. CAP_membership.member.create. */
    public static final String CAPABILITY_PREFIX = "CAP_";

    private GymoraRoles() {
    }
}
