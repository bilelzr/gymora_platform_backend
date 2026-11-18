package com.gymora.subscription;

import com.gymora.security.capability.DefaultProfileGrants;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Capabilities of the subscription module, and what the starting profiles get.
 * Keep the backfill migration V202611190901 in step with {@link #grants()}.
 */
@Component
public class SubscriptionCapabilities implements DefaultProfileGrants {

    public static final String PLAN_READ = "subscription.plan.read";
    public static final String PLAN_MANAGE = "subscription.plan.manage";
    public static final String SUBSCRIPTION_READ = "subscription.subscription.read";
    public static final String SUBSCRIPTION_SELL = "subscription.subscription.sell";
    public static final String SUBSCRIPTION_FREEZE = "subscription.subscription.freeze";
    public static final String SUBSCRIPTION_CANCEL = "subscription.subscription.cancel";
    /** Also decides whether amounts appear in subscription views: trainers see dates, not money. */
    public static final String PAYMENT_READ = "subscription.payment.read";
    public static final String PAYMENT_RECORD = "subscription.payment.record";
    public static final String PAYMENT_VOID = "subscription.payment.void";

    @Override
    public Map<DefaultProfile, Set<String>> grants() {
        return Map.of(
                DefaultProfile.MANAGER,
                Set.of(PLAN_READ, PLAN_MANAGE, SUBSCRIPTION_READ, SUBSCRIPTION_SELL, SUBSCRIPTION_FREEZE,
                        SUBSCRIPTION_CANCEL, PAYMENT_READ, PAYMENT_RECORD, PAYMENT_VOID),
                DefaultProfile.RECEPTIONIST,
                Set.of(PLAN_READ, SUBSCRIPTION_READ, SUBSCRIPTION_SELL, SUBSCRIPTION_FREEZE, PAYMENT_READ,
                        PAYMENT_RECORD),
                DefaultProfile.TRAINER,
                Set.of(SUBSCRIPTION_READ));
    }
}
