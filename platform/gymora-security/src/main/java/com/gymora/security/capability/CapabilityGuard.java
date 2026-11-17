package com.gymora.security.capability;

import com.gymora.common.tenant.TenantContext;
import com.gymora.security.GymoraRoles;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Evaluates {@link RequiresCapability}. Referenced by name from its SpEL expression. */
@Component("capabilityGuard")
public class CapabilityGuard {

    public boolean has(String capability) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || TenantContext.current().isEmpty()) {
            return false;
        }
        boolean granted = false;
        String wanted = GymoraRoles.CAPABILITY_PREFIX + capability;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String name = authority.getAuthority();
            if ((GymoraRoles.ROLE_PREFIX + GymoraRoles.PLATFORM_ADMIN).equals(name)) {
                return false; // platform admins never act on a gym's data
            }
            if ((GymoraRoles.ROLE_PREFIX + GymoraRoles.GYM_OWNER).equals(name) || wanted.equals(name)) {
                granted = true;
            }
        }
        return granted;
    }
}
