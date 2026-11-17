package com.gymora.membership.domain;

import com.gymora.common.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** A staff note about a member. Notes are never edited: they form a dated log. */
@Entity
@Table(name = "membership_member_note")
public class MemberNote extends TenantScopedEntity {

    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Column(name = "body", nullable = false, updatable = false, length = 2000)
    private String body;

    /** The author's name when the note was written (their user id is in created_by). */
    @Column(name = "author_name", nullable = false, updatable = false, length = 150)
    private String authorName;

    protected MemberNote() {
    }

    public MemberNote(UUID memberId, String body, String authorName) {
        this.memberId = memberId;
        this.body = body;
        this.authorName = authorName;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public String getBody() {
        return body;
    }

    public String getAuthorName() {
        return authorName;
    }
}
