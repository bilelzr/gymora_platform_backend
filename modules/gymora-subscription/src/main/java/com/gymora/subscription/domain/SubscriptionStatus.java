package com.gymora.subscription.domain;

/** Worked out from the dates on each read; never stored. */
public enum SubscriptionStatus {
    /** Sold, starts later (e.g. a renewal). */
    UPCOMING,
    ACTIVE,
    /** Active, but paused today by a freeze. */
    FROZEN,
    EXPIRED,
    CANCELLED
}
