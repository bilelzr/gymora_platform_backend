package com.gymora.security.capability;

import java.util.Set;
import java.util.UUID;

/**
 * Supplies the capabilities a staff user holds in a gym, from the profiles assigned to them.
 *
 * <p>Implemented by the access module. Gym owners and platform admins are never looked up: owners
 * hold every capability in their gym, platform admins none.
 */
public interface CapabilityProvider {

    Set<String> capabilitiesOf(String userId, UUID tenantId);
}
