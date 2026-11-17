package com.gymora.access.application;

import com.gymora.access.domain.StaffMember;
import com.gymora.common.error.BusinessRuleException;
import com.gymora.security.user.CurrentUser;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Rules that stop staff from giving themselves or others more power than they have:
 *
 * <ol>
 *   <li>You can only grant capabilities you hold yourself.</li>
 *   <li>You can only edit a profile, or manage a staff member, whose capabilities you all hold.</li>
 *   <li>You can't change your own profiles or disable your own account.</li>
 * </ol>
 *
 * Gym owners hold every capability in their gym, so these rules never block them.
 */
@Component
public class AccessPolicy {

    /** Throws unless the current user holds every one of these capabilities. */
    public void requireHoldsAll(Collection<String> capabilities) {
        CurrentUser actor = CurrentUser.get().orElseThrow();
        if (actor.isGymOwner()) {
            return;
        }
        Set<String> missing = new TreeSet<>(capabilities);
        missing.removeAll(actor.capabilities());
        if (!missing.isEmpty()) {
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "error.access_escalation", String.join(", ", missing));
        }
    }

    public void requireNotSelf(StaffMember target) {
        CurrentUser actor = CurrentUser.get().orElseThrow();
        if (target.getUserId().equals(actor.userId())) {
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "error.access_self");
        }
    }
}
