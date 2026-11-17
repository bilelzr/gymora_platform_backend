package com.gymora.access.api;

import com.gymora.access.application.AccessCapabilities;
import com.gymora.access.application.StaffService;
import com.gymora.access.application.StaffService.NewStaff;
import com.gymora.access.application.StaffView;
import com.gymora.access.domain.StaffStatus;
import com.gymora.common.web.PageResponse;
import com.gymora.security.capability.RequiresCapability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff")
@Tag(name = "Access · Staff", description = "Gym employees with a login account and profiles.")
public class StaffController {

    private final StaffService service;

    public StaffController(StaffService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresCapability(AccessCapabilities.STAFF_READ)
    @Operation(summary = "Search staff by name, email or phone")
    public PageResponse<StaffView> search(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) StaffStatus status,
                                          @RequestParam(required = false) UUID profileId,
                                          @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC)
                                          Pageable pageable) {
        return PageResponse.of(service.search(q, status, profileId, pageable), view -> view);
    }

    @GetMapping("/{id}")
    @RequiresCapability(AccessCapabilities.STAFF_READ)
    public StaffView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @RequiresCapability(AccessCapabilities.STAFF_MANAGE)
    @Operation(summary = "Add a staff member",
            description = "Creates their login account; they receive an email to set their password.")
    public ResponseEntity<StaffView> create(@Valid @RequestBody CreateStaffRequest request) {
        StaffView created = service.create(new NewStaff(request.email(), request.firstName().trim(),
                request.lastName().trim(), blankToNull(request.phone()), request.profileIds()));
        return ResponseEntity.created(URI.create("/api/v1/staff/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @RequiresCapability(AccessCapabilities.STAFF_MANAGE)
    @Operation(summary = "Update a staff member's name and phone")
    public StaffView update(@PathVariable UUID id, @Valid @RequestBody UpdateStaffRequest request) {
        return service.updateDetails(id, request.firstName().trim(), request.lastName().trim(),
                blankToNull(request.phone()));
    }

    @PutMapping("/{id}/profiles")
    @RequiresCapability(AccessCapabilities.STAFF_MANAGE)
    @Operation(summary = "Replace a staff member's profiles", description = "You can't change your own profiles.")
    public StaffView replaceProfiles(@PathVariable UUID id, @Valid @RequestBody ProfilesRequest request) {
        return service.replaceProfiles(id, request.profileIds());
    }

    @PostMapping("/{id}/disable")
    @RequiresCapability(AccessCapabilities.STAFF_MANAGE)
    @Operation(summary = "Disable a staff member: they can no longer sign in")
    public StaffView disable(@PathVariable UUID id) {
        return service.disable(id);
    }

    @PostMapping("/{id}/enable")
    @RequiresCapability(AccessCapabilities.STAFF_MANAGE)
    public StaffView enable(@PathVariable UUID id) {
        return service.enable(id);
    }

    @PostMapping("/{id}/resend-invitation")
    @RequiresCapability(AccessCapabilities.STAFF_MANAGE)
    @Operation(summary = "Email the staff member a new link to set their password")
    public ResponseEntity<Void> resendInvitation(@PathVariable UUID id) {
        service.resendInvitation(id);
        return ResponseEntity.accepted().build();
    }

    public record CreateStaffRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 30) String phone,
            Set<UUID> profileIds) {
    }

    public record UpdateStaffRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 30) String phone) {
    }

    public record ProfilesRequest(@NotNull Set<UUID> profileIds) {
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
