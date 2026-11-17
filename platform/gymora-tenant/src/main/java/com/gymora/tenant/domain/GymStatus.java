package com.gymora.tenant.domain;

public enum GymStatus {
    /** The gym's users can sign in and work. */
    ACTIVE,
    /** Every request from the gym's users is refused (for example: unpaid subscription). Data is kept. */
    SUSPENDED
}
