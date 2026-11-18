package com.gymora.membership;

import com.gymora.security.capability.DefaultProfileGrants;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Capabilities of the membership module, and what the starting profiles get.
 * Keep the backfill migration V202611180901 in step with {@link #grants()}.
 */
@Component
public class MembershipCapabilities implements DefaultProfileGrants {

    public static final String MEMBER_READ = "membership.member.read";
    public static final String MEMBER_CREATE = "membership.member.create";
    public static final String MEMBER_UPDATE = "membership.member.update";
    public static final String MEMBER_ARCHIVE = "membership.member.archive";
    public static final String NOTE_READ = "membership.note.read";
    public static final String NOTE_CREATE = "membership.note.create";

    @Override
    public Map<DefaultProfile, Set<String>> grants() {
        return Map.of(
                DefaultProfile.MANAGER,
                Set.of(MEMBER_READ, MEMBER_CREATE, MEMBER_UPDATE, MEMBER_ARCHIVE, NOTE_READ, NOTE_CREATE),
                DefaultProfile.RECEPTIONIST,
                Set.of(MEMBER_READ, MEMBER_CREATE, MEMBER_UPDATE, NOTE_READ, NOTE_CREATE),
                DefaultProfile.TRAINER,
                Set.of(MEMBER_READ, NOTE_READ));
    }
}
