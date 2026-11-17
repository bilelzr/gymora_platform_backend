package com.gymora.access.api;

import com.gymora.access.application.StaffView.ProfileRef;
import com.gymora.access.domain.StaffRepository;
import com.gymora.security.user.CurrentUser;
import com.gymora.tenant.domain.GymRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who the caller is: user, gym, role, profiles and capabilities. The Angular app uses it to show
 * or hide menus and buttons. Open to every signed-in user.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Session")
public class MeController {

    private final StaffRepository staff;
    private final GymRepository gyms;

    public MeController(StaffRepository staff, GymRepository gyms) {
        this.staff = staff;
        this.gyms = gyms;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Current user, gym, profiles and capabilities",
            description = "capabilities is [\"*\"] for a gym owner, who holds every capability in their gym. "
                    + "gym is null for platform admins.")
    public MeResponse me() {
        CurrentUser user = CurrentUser.get().orElseThrow();
        UUID staffId = null;
        List<ProfileRef> profiles = List.of();
        GymSummary gym = null;
        if (user.tenantId() != null) {
            gym = gyms.findById(user.tenantId())
                    .map(g -> new GymSummary(g.getId(), g.getName(), g.getCity(), g.getCurrency(), g.getTimeZone()))
                    .orElse(null);
            if (!user.isGymOwner()) {
                var member = staff.findByUserId(user.userId()).orElse(null);
                if (member != null) {
                    staffId = member.getId();
                    profiles = member.getProfiles().stream()
                            .map(profile -> new ProfileRef(profile.getId(), profile.getName()))
                            .sorted(Comparator.comparing(ProfileRef::name))
                            .toList();
                }
            }
        }
        return new MeResponse(user.userId(), user.username(), user.email(), user.fullName(), user.tenantId(), gym,
                user.roles(), user.isGymOwner() ? Set.of("*") : user.capabilities(), staffId, profiles);
    }

    public record MeResponse(String userId, String username, String email, String fullName, UUID tenantId,
                             GymSummary gym, Set<String> roles, Set<String> capabilities, UUID staffId,
                             List<ProfileRef> profiles) {
    }

    /** The signed-in user's gym, for the app's header and sidebar. */
    public record GymSummary(UUID id, String name, String city, String currency, String timeZone) {
    }
}
