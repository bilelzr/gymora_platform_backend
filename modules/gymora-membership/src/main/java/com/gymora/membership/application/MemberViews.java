package com.gymora.membership.application;

import com.gymora.membership.domain.Gender;
import com.gymora.membership.domain.Member;
import com.gymora.membership.domain.MemberNote;
import com.gymora.membership.domain.MemberStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class MemberViews {

    private MemberViews() {
    }

    public record MemberView(UUID id, String memberNumber, String firstName, String lastName, String phone,
                             String email, Gender gender, LocalDate birthDate, String address,
                             String emergencyContactName, String emergencyContactPhone, MemberStatus status,
                             Instant archivedAt, Instant createdAt, Instant updatedAt, long version) {

        public static MemberView of(Member m) {
            return new MemberView(m.getId(), m.getMemberNumber(), m.getFirstName(), m.getLastName(), m.getPhone(),
                    m.getEmail(), m.getGender(), m.getBirthDate(), m.getAddress(), m.getEmergencyContactName(),
                    m.getEmergencyContactPhone(), m.getStatus(), m.getArchivedAt(), m.getCreatedAt(),
                    m.getUpdatedAt(), m.getVersion());
        }
    }

    public record NoteView(UUID id, String body, String authorName, Instant createdAt) {

        public static NoteView of(MemberNote note) {
            return new NoteView(note.getId(), note.getBody(), note.getAuthorName(), note.getCreatedAt());
        }
    }
}
