package com.gymora.subscription.domain;

public enum PlanStatus {
    ACTIVE,
    /** No longer sold. Subscriptions already sold are not affected. */
    ARCHIVED
}
