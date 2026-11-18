package com.gymora.subscription.application;

import com.gymora.membership.application.MemberDirectory.MemberSummary;
import com.gymora.subscription.domain.DurationUnit;
import com.gymora.subscription.domain.Freeze;
import com.gymora.subscription.domain.Payment;
import com.gymora.subscription.domain.PaymentMethod;
import com.gymora.subscription.domain.Plan;
import com.gymora.subscription.domain.PlanStatus;
import com.gymora.subscription.domain.Subscription;
import com.gymora.subscription.domain.SubscriptionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** What the API returns. Amounts are in millimes (1 TND = 1000). */
public final class SubscriptionViews {

    private SubscriptionViews() {
    }

    public record PlanView(UUID id, String name, String description, int durationValue, DurationUnit durationUnit,
                           long priceMillimes, Integer sessionCount, int maxFreezeDays, PlanStatus status,
                           long runningSubscriptions, Instant archivedAt, Instant createdAt, long version) {

        static PlanView of(Plan p, long running) {
            return new PlanView(p.getId(), p.getName(), p.getDescription(), p.getDurationValue(), p.getDurationUnit(),
                    p.getPriceMillimes(), p.getSessionCount(), p.getMaxFreezeDays(), p.getStatus(), running,
                    p.getArchivedAt(), p.getCreatedAt(), p.getVersion());
        }
    }

    /**
     * A subscription as of the gym's today. The money fields are null for callers without
     * {@code subscription.payment.read}.
     */
    public record SubscriptionView(UUID id, UUID memberId, UUID planId, String planName, int durationValue,
                                   DurationUnit durationUnit, Integer sessionCount, LocalDate startDate,
                                   LocalDate endDate, SubscriptionStatus status, int daysTotal, int daysLeft,
                                   int maxFreezeDays, int frozenDays, int freezeDaysLeft, List<FreezeView> freezes,
                                   Long priceMillimes, Long discountMillimes, Long amountDueMillimes,
                                   Long paidMillimes, Long remainingMillimes, Instant cancelledAt,
                                   LocalDate cancelledOn, String cancelReason, String cancelledByName,
                                   Instant createdAt, long version) {

        static SubscriptionView of(Subscription s, List<Freeze> freezes, LocalDate today, boolean showMoney) {
            boolean frozenToday = freezes.stream().anyMatch(f -> f.covers(today));
            SubscriptionStatus status = s.statusOn(today, frozenToday);
            int total = (int) ChronoUnit.DAYS.between(s.getStartDate(), s.getEndDate()) + 1;
            int left = switch (status) {
                case UPCOMING -> total;
                case ACTIVE, FROZEN -> (int) ChronoUnit.DAYS.between(today, s.getEndDate()) + 1;
                case EXPIRED, CANCELLED -> 0;
            };
            return new SubscriptionView(s.getId(), s.getMemberId(), s.getPlanId(), s.getPlanName(),
                    s.getDurationValue(), s.getDurationUnit(), s.getSessionCount(), s.getStartDate(), s.getEndDate(),
                    status, total, left, s.getMaxFreezeDays(), s.getFrozenDays(), s.freezeDaysLeft(),
                    freezes.stream().map(FreezeView::of).toList(),
                    showMoney ? s.getPriceMillimes() : null, showMoney ? s.getDiscountMillimes() : null,
                    showMoney ? s.getAmountDueMillimes() : null, showMoney ? s.getPaidMillimes() : null,
                    showMoney ? s.remainingMillimes() : null, s.getCancelledAt(), s.getCancelledOn(),
                    s.getCancelReason(), s.getCancelledByName(), s.getCreatedAt(), s.getVersion());
        }
    }

    public record FreezeView(UUID id, LocalDate startDate, LocalDate endDate, int days, String reason,
                             boolean endedEarly) {

        static FreezeView of(Freeze f) {
            return new FreezeView(f.getId(), f.getStartDate(), f.getEndDate(), f.getDays(), f.getReason(),
                    f.isEndedEarly());
        }
    }

    public record MemberRef(UUID id, String memberNumber, String firstName, String lastName, String phone) {

        static MemberRef of(MemberSummary m) {
            return m == null ? null : new MemberRef(m.id(), m.memberNumber(), m.firstName(), m.lastName(), m.phone());
        }
    }

    /** A line of the subscriptions list. {@code renewed}: another subscription is already queued after it. */
    public record SubscriptionRow(SubscriptionView subscription, MemberRef member, boolean renewed) {
    }

    /** Counts behind the filters of the subscriptions page and the home page. Money: null without payment.read. */
    public record SubscriptionCounts(long running, long expiringSoon, long expiredRecently, long frozen,
                                     Long unpaid, Long unpaidMillimes) {
    }

    public record PaymentView(UUID id, UUID subscriptionId, UUID memberId, String planName, String receiptNumber,
                              long amountMillimes, PaymentMethod method, Instant paidAt, LocalDate paidOn,
                              String receivedByName, String note, Instant voidedAt, String voidedByName,
                              String voidReason) {

        static PaymentView of(Payment p, String planName) {
            return new PaymentView(p.getId(), p.getSubscriptionId(), p.getMemberId(), planName, p.getReceiptNumber(),
                    p.getAmountMillimes(), p.getMethod(), p.getPaidAt(), p.getPaidOn(), p.getReceivedByName(),
                    p.getNote(), p.getVoidedAt(), p.getVoidedByName(), p.getVoidReason());
        }
    }

    /** A sale: the new subscription, and the payment taken with it if any. */
    public record SaleView(SubscriptionView subscription, PaymentView payment) {
    }

    /** The day's cash, to check against the till at closing. Voided payments are left out. */
    public record DayCash(LocalDate date, long totalMillimes, int count, List<StaffTotal> byStaff,
                          List<DayPayment> payments) {
    }

    public record StaffTotal(String name, long totalMillimes, int count) {
    }

    public record DayPayment(PaymentView payment, MemberRef member) {
    }
}
