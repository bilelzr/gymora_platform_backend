package com.gymora.membership.domain;

public enum MemberStatus {
    ACTIVE,
    /** No longer a member. Read-only, kept for history; can be reactivated. */
    ARCHIVED
}
