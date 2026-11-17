package com.gymora.access.domain;

public enum StaffStatus {
    ACTIVE,
    /** Cannot sign in (disabled in Keycloak) and holds no capability. History is kept. */
    DISABLED
}
