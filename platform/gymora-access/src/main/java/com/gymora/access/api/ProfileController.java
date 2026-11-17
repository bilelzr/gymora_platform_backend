package com.gymora.access.api;

import com.gymora.access.application.AccessCapabilities;
import com.gymora.access.application.ProfileService;
import com.gymora.access.application.ProfileView;
import com.gymora.security.capability.RequiresCapability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles")
@Tag(name = "Access · Profiles", description = "Named sets of capabilities assigned to staff, defined per gym.")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresCapability(AccessCapabilities.PROFILE_READ)
    @Operation(summary = "List the gym's profiles with their capabilities and how many staff hold each")
    public List<ProfileView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @RequiresCapability(AccessCapabilities.PROFILE_READ)
    public ProfileView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @RequiresCapability(AccessCapabilities.PROFILE_MANAGE)
    @Operation(summary = "Create a profile", description = "You can only include capabilities you hold yourself.")
    public ResponseEntity<ProfileView> create(@Valid @RequestBody CreateProfileRequest request) {
        ProfileView created = service.create(request.name().trim(), blankToNull(request.description()),
                request.capabilities());
        return ResponseEntity.created(URI.create("/api/v1/profiles/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @RequiresCapability(AccessCapabilities.PROFILE_MANAGE)
    @Operation(summary = "Rename a profile or change its description")
    public ProfileView rename(@PathVariable UUID id, @Valid @RequestBody RenameProfileRequest request) {
        return service.rename(id, request.name().trim(), blankToNull(request.description()));
    }

    @PutMapping("/{id}/capabilities")
    @RequiresCapability(AccessCapabilities.PROFILE_MANAGE)
    @Operation(summary = "Replace a profile's capabilities",
            description = "Takes effect for every staff member holding the profile on their next request.")
    public ProfileView replaceCapabilities(@PathVariable UUID id, @Valid @RequestBody CapabilitiesRequest request) {
        return service.replaceCapabilities(id, request.capabilities());
    }

    @DeleteMapping("/{id}")
    @RequiresCapability(AccessCapabilities.PROFILE_MANAGE)
    @Operation(summary = "Delete a profile that no staff member holds")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record CreateProfileRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 300) String description,
            @NotNull Set<@NotBlank String> capabilities) {
    }

    public record RenameProfileRequest(@NotBlank @Size(max = 80) String name, @Size(max = 300) String description) {
    }

    public record CapabilitiesRequest(@NotNull Set<@NotBlank String> capabilities) {
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
