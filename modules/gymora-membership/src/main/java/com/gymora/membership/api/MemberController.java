package com.gymora.membership.api;

import com.gymora.common.web.PageResponse;
import com.gymora.membership.MembershipCapabilities;
import com.gymora.membership.application.MemberService;
import com.gymora.membership.application.MemberViews.MemberView;
import com.gymora.membership.application.MemberViews.NoteView;
import com.gymora.membership.application.PhoneNumbers;
import com.gymora.membership.domain.Gender;
import com.gymora.membership.domain.Member.MemberDetails;
import com.gymora.membership.domain.MemberStatus;
import com.gymora.security.capability.RequiresCapability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping("/api/v1/members")
@Tag(name = "Membership · Members", description = "The gym's members: registration, profile, search, archive and notes.")
public class MemberController {

    private final MemberService service;

    public MemberController(MemberService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresCapability(MembershipCapabilities.MEMBER_READ)
    @Operation(summary = "Search members by name, member number, phone or email")
    public PageResponse<MemberView> search(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) MemberStatus status,
                                           @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                           Pageable pageable) {
        return PageResponse.of(service.search(q, status, pageable), view -> view);
    }

    @GetMapping("/{id}")
    @RequiresCapability(MembershipCapabilities.MEMBER_READ)
    public MemberView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @RequiresCapability(MembershipCapabilities.MEMBER_CREATE)
    @Operation(summary = "Register a member", description = "Gives them the gym's next member number (M-000123).")
    public ResponseEntity<MemberView> register(@Valid @RequestBody MemberRequest request) {
        MemberView created = service.register(request.toDetails());
        return ResponseEntity.created(URI.create("/api/v1/members/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @RequiresCapability(MembershipCapabilities.MEMBER_UPDATE)
    @Operation(summary = "Update a member's details", description = "Archived members can't be changed.")
    public MemberView update(@PathVariable UUID id, @Valid @RequestBody MemberRequest request) {
        return service.update(id, request.toDetails());
    }

    @PostMapping("/{id}/archive")
    @RequiresCapability(MembershipCapabilities.MEMBER_ARCHIVE)
    @Operation(summary = "Archive a member: kept with their history, but no longer active")
    public MemberView archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    @PostMapping("/{id}/reactivate")
    @RequiresCapability(MembershipCapabilities.MEMBER_ARCHIVE)
    public MemberView reactivate(@PathVariable UUID id) {
        return service.reactivate(id);
    }

    @GetMapping("/{id}/notes")
    @RequiresCapability(MembershipCapabilities.NOTE_READ)
    @Operation(summary = "Staff notes about a member, newest first")
    public List<NoteView> notes(@PathVariable UUID id) {
        return service.notes(id);
    }

    @PostMapping("/{id}/notes")
    @RequiresCapability(MembershipCapabilities.NOTE_CREATE)
    @Operation(summary = "Add a staff note about a member")
    public ResponseEntity<NoteView> addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        NoteView created = service.addNote(id, request.body().trim());
        return ResponseEntity.status(201).body(created);
    }

    public record MemberRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(max = 30) String phone,
            @Email @Size(max = 255) String email,
            Gender gender,
            @Past LocalDate birthDate,
            @Size(max = 300) String address,
            @Size(max = 150) String emergencyContactName,
            @Size(max = 30) String emergencyContactPhone) {

        MemberDetails toDetails() {
            return new MemberDetails(firstName.trim(), lastName.trim(), PhoneNumbers.normalize(phone),
                    blankToNull(email) == null ? null : email.trim().toLowerCase(), gender, birthDate,
                    blankToNull(address), blankToNull(emergencyContactName),
                    PhoneNumbers.normalizeOptional(emergencyContactPhone));
        }
    }

    public record NoteRequest(@NotBlank @Size(max = 2000) String body) {
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
