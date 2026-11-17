package com.gymora.security.identity;

import java.util.UUID;

/**
 * Manages login accounts in the identity provider (Keycloak). GYMORA keeps its own records of gyms
 * and staff; this only handles the accounts people sign in with.
 */
public interface IdentityAdmin {

    /**
     * Creates an enabled account linked to a gym and returns its user id.
     *
     * @throws IdentityConflictException when the email is already used by another account
     */
    String createUser(NewIdentity identity);

    void updateNames(String userId, String firstName, String lastName);

    void setEnabled(String userId, boolean enabled);

    void grantRealmRole(String userId, String role);

    /** Emails the user a link to set their password and verify their email. */
    void sendSetupEmail(String userId);

    void deleteUser(String userId);

    record NewIdentity(String email, String firstName, String lastName, UUID tenantId) {
    }
}
