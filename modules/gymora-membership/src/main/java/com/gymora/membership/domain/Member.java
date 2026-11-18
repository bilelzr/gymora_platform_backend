package com.gymora.membership.domain;

import com.gymora.common.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** A member of one gym. Archived instead of deleted, so history is kept. */
@Entity
@Table(name = "membership_member")
public class Member extends TenantScopedEntity {

    /** Per-gym number shown on cards and receipts, e.g. M-000123. Never changes. */
    @Column(name = "member_number", nullable = false, updatable = false, length = 20)
    private String memberNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "email")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 30)
    private String emergencyContactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Member() {
    }

    public Member(String memberNumber, MemberDetails details) {
        this.memberNumber = memberNumber;
        apply(details);
    }

    public void apply(MemberDetails details) {
        this.firstName = details.firstName();
        this.lastName = details.lastName();
        this.phone = details.phone();
        this.email = details.email();
        this.gender = details.gender();
        this.birthDate = details.birthDate();
        this.address = details.address();
        this.emergencyContactName = details.emergencyContactName();
        this.emergencyContactPhone = details.emergencyContactPhone();
    }

    public void archive(Instant at) {
        this.status = MemberStatus.ARCHIVED;
        this.archivedAt = at;
    }

    public void reactivate() {
        this.status = MemberStatus.ACTIVE;
        this.archivedAt = null;
    }

    public boolean isArchived() {
        return status == MemberStatus.ARCHIVED;
    }

    public String getMemberNumber() {
        return memberNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public Gender getGender() {
        return gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getAddress() {
        return address;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    /** Editable details of a member; phone numbers already normalized. */
    public record MemberDetails(String firstName, String lastName, String phone, String email, Gender gender,
                                LocalDate birthDate, String address, String emergencyContactName,
                                String emergencyContactPhone) {
    }
}
