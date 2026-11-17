package com.gymora.app.support;

import com.gymora.security.identity.IdentityAdmin;
import com.gymora.security.identity.IdentityConflictException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Stands in for Keycloak in tests: records every call instead of creating real accounts. */
public class RecordingIdentityAdmin implements IdentityAdmin {

    public final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    public final Map<String, NewIdentity> users = new ConcurrentHashMap<>();
    public final Set<String> takenEmails = ConcurrentHashMap.newKeySet();

    @Override
    public String createUser(NewIdentity identity) {
        if (takenEmails.contains(identity.email())) {
            throw new IdentityConflictException(identity.email());
        }
        String userId = "kc-" + UUID.randomUUID();
        users.put(userId, identity);
        calls.add("createUser:" + identity.email() + ":" + identity.tenantId());
        return userId;
    }

    @Override
    public void updateNames(String userId, String firstName, String lastName) {
        calls.add("updateNames:" + userId + ":" + firstName + " " + lastName);
    }

    @Override
    public void setEnabled(String userId, boolean enabled) {
        calls.add("setEnabled:" + userId + ":" + enabled);
    }

    @Override
    public void grantRealmRole(String userId, String role) {
        calls.add("grantRealmRole:" + userId + ":" + role);
    }

    @Override
    public void sendSetupEmail(String userId) {
        calls.add("sendSetupEmail:" + userId);
    }

    @Override
    public void deleteUser(String userId) {
        users.remove(userId);
        calls.add("deleteUser:" + userId);
    }

    public void reset() {
        calls.clear();
        users.clear();
        takenEmails.clear();
    }
}
