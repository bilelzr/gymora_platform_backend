package com.gymora.tenant.api;

import com.gymora.common.web.PageResponse;
import com.gymora.tenant.application.GymService;
import com.gymora.tenant.application.GymService.Owner;
import com.gymora.tenant.domain.Gym;
import com.gymora.tenant.domain.Gym.GymDetails;
import com.gymora.tenant.domain.GymStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
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

/**
 * Gym management for the GYMORA platform admin. Everything under /api/v1/platform requires the
 * PLATFORM_ADMIN role (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/platform/gyms")
@Tag(name = "Platform · Gyms")
public class PlatformGymController {

    private final GymService service;

    public PlatformGymController(GymService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a gym and its owner's account", description =
            "The owner gets the GYM_OWNER role and an email to set their password. The gym starts with "
                    + "default Manager, Receptionist and Trainer profiles.")
    public ResponseEntity<GymResponse> create(@Valid @RequestBody CreateGymRequest request) {
        Gym gym = service.create(request.details(),
                new Owner(request.owner().email().trim().toLowerCase(), request.owner().firstName().trim(),
                        request.owner().lastName().trim()));
        return ResponseEntity.created(URI.create("/api/v1/platform/gyms/" + gym.getId())).body(GymResponse.of(gym));
    }

    @GetMapping
    @Operation(summary = "Search gyms by name, city or owner email")
    public PageResponse<GymResponse> search(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) GymStatus status,
                                            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
                                            Pageable pageable) {
        return PageResponse.of(service.search(q, status, pageable), GymResponse::of);
    }

    @GetMapping("/{id}")
    public GymResponse get(@PathVariable UUID id) {
        return GymResponse.of(service.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a gym's details")
    public GymResponse update(@PathVariable UUID id, @Valid @RequestBody GymDetailsRequest request) {
        return GymResponse.of(service.update(id, request.toDetails()));
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend a gym: all its users are refused until it is reactivated")
    public GymResponse suspend(@PathVariable UUID id, @Valid @RequestBody SuspendRequest request) {
        return GymResponse.of(service.suspend(id, request.reason().trim()));
    }

    @PostMapping("/{id}/activate")
    public GymResponse activate(@PathVariable UUID id) {
        return GymResponse.of(service.activate(id));
    }

    @PostMapping("/{id}/owner/resend-invitation")
    @Operation(summary = "Email the owner a new link to set their password")
    public ResponseEntity<Void> resendInvitation(@PathVariable UUID id) {
        service.resendOwnerInvitation(id);
        return ResponseEntity.accepted().build();
    }

    // ---------- DTOs ----------

    public record GymDetailsRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 100) String city,
            @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @Size(max = 30) String phone,
            @Email @Size(max = 255) String email,
            @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @Size(max = 50) String timeZone) {

        GymDetails toDetails() {
            return new GymDetails(name.trim(), blankToNull(city),
                    countryCode == null ? "TN" : countryCode.toUpperCase(),
                    blankToNull(phone), blankToNull(email),
                    currency == null ? "TND" : currency.toUpperCase(),
                    timeZone == null || timeZone.isBlank() ? "Africa/Tunis" : timeZone);
        }
    }

    public record CreateGymRequest(@NotNull @Valid GymDetailsRequest gym, @NotNull @Valid OwnerRequest owner) {
        GymDetails details() {
            return gym.toDetails();
        }
    }

    public record OwnerRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName) {
    }

    public record SuspendRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record GymResponse(UUID id, String name, String city, String countryCode, String phone, String email,
                              String currency, String timeZone, GymStatus status, String suspendedReason,
                              Instant suspendedAt, OwnerResponse owner, Instant createdAt) {
        static GymResponse of(Gym gym) {
            return new GymResponse(gym.getId(), gym.getName(), gym.getCity(), gym.getCountryCode(), gym.getPhone(),
                    gym.getEmail(), gym.getCurrency(), gym.getTimeZone(), gym.getStatus(), gym.getSuspendedReason(),
                    gym.getSuspendedAt(),
                    new OwnerResponse(gym.getOwnerUserId(), gym.getOwnerEmail(), gym.getOwnerFirstName(),
                            gym.getOwnerLastName()),
                    gym.getCreatedAt());
        }
    }

    public record OwnerResponse(String userId, String email, String firstName, String lastName) {
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
