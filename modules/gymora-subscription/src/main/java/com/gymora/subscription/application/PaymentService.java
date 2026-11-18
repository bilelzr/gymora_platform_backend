package com.gymora.subscription.application;

import com.gymora.common.error.BusinessRuleException;
import com.gymora.common.error.NotFoundException;
import com.gymora.membership.application.MemberDirectory;
import com.gymora.membership.application.MemberDirectory.MemberSummary;
import com.gymora.subscription.application.SubscriptionViews.DayCash;
import com.gymora.subscription.application.SubscriptionViews.DayPayment;
import com.gymora.subscription.application.SubscriptionViews.MemberRef;
import com.gymora.subscription.application.SubscriptionViews.PaymentView;
import com.gymora.subscription.application.SubscriptionViews.StaffTotal;
import com.gymora.subscription.domain.Payment;
import com.gymora.subscription.domain.PaymentMethod;
import com.gymora.subscription.domain.PaymentRepository;
import com.gymora.subscription.domain.Subscription;
import com.gymora.subscription.domain.SubscriptionRepository;
import com.gymora.tenant.application.GymCalendar;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cash received for subscriptions. Payments are never deleted; a mistake is voided with a reason. */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final SubscriptionRepository subscriptions;
    private final ReceiptNumberGenerator receipts;
    private final MemberDirectory members;
    private final GymCalendar calendar;

    public PaymentService(PaymentRepository payments, SubscriptionRepository subscriptions,
                          ReceiptNumberGenerator receipts, MemberDirectory members, GymCalendar calendar) {
        this.payments = payments;
        this.subscriptions = subscriptions;
        this.receipts = receipts;
        this.members = members;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public List<PaymentView> forSubscription(UUID subscriptionId) {
        Subscription s = findSubscription(subscriptionId);
        return payments.findBySubscriptionIdOrderByPaidAtDesc(subscriptionId).stream()
                .map(p -> PaymentView.of(p, s.getPlanName())).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentView> forMember(UUID memberId) {
        members.require(memberId);
        List<Payment> list = payments.findByMemberIdOrderByPaidAtDesc(memberId);
        return withPlanNames(list);
    }

    /** Records cash received for a subscription; at most what remains to pay. */
    @Transactional
    public PaymentView record(UUID subscriptionId, long amountMillimes, String note) {
        return record(findSubscription(subscriptionId), amountMillimes, note);
    }

    PaymentView record(Subscription subscription, long amountMillimes, String note) {
        if (subscription.isCancelled()) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.subscription_cancelled");
        }
        long remaining = subscription.remainingMillimes();
        if (remaining == 0) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.payment_nothing_due");
        }
        if (amountMillimes > remaining) {
            throw new BusinessRuleException("error.payment_too_high", Formats.amount(remaining));
        }
        Instant now = calendar.now();
        Payment payment = payments.save(new Payment(subscription, receipts.next(), amountMillimes, PaymentMethod.CASH,
                now, calendar.dateOf(now), Formats.currentUserName(), blankToNull(note)));
        subscription.paymentReceived(amountMillimes);
        return PaymentView.of(payment, subscription.getPlanName());
    }

    /** Cancels a payment recorded by mistake. The receipt number is not reused. */
    @Transactional
    public PaymentView voidPayment(UUID paymentId, String reason) {
        Payment payment = payments.findById(paymentId).orElseThrow(() -> new NotFoundException("payment", paymentId));
        if (payment.isVoided()) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.payment_already_voided");
        }
        Subscription subscription = findSubscription(payment.getSubscriptionId());
        payment.voidIt(calendar.now(), Formats.currentUserName(), reason);
        subscription.paymentVoided(payment.getAmountMillimes());
        payments.flush();
        return PaymentView.of(payment, subscription.getPlanName());
    }

    /** Cash received on a gym day (today by default), with totals per staff member. */
    @Transactional(readOnly = true)
    public DayCash day(LocalDate date) {
        LocalDate day = date != null ? date : calendar.today();
        List<Payment> list = payments.findByPaidOnOrderByPaidAtDesc(day).stream().filter(p -> !p.isVoided()).toList();
        List<PaymentView> views = withPlanNames(list);
        Map<UUID, MemberSummary> people = members.findAll(list.stream().map(Payment::getMemberId).distinct().toList());

        Map<String, long[]> perStaff = new LinkedHashMap<>();
        for (Payment p : list) {
            long[] totals = perStaff.computeIfAbsent(p.getReceivedByName(), name -> new long[2]);
            totals[0] += p.getAmountMillimes();
            totals[1]++;
        }
        List<StaffTotal> byStaff = perStaff.entrySet().stream()
                .map(e -> new StaffTotal(e.getKey(), e.getValue()[0], (int) e.getValue()[1]))
                .sorted(Comparator.comparingLong(StaffTotal::totalMillimes).reversed())
                .toList();
        List<DayPayment> items = views.stream()
                .map(v -> new DayPayment(v, MemberRef.of(people.get(v.memberId()))))
                .toList();
        long total = list.stream().mapToLong(Payment::getAmountMillimes).sum();
        return new DayCash(day, total, list.size(), byStaff, items);
    }

    private List<PaymentView> withPlanNames(List<Payment> list) {
        Map<UUID, String> planNames = subscriptions
                .findAllById(list.stream().map(Payment::getSubscriptionId).distinct().toList()).stream()
                .collect(Collectors.toMap(Subscription::getId, Subscription::getPlanName));
        return list.stream().map(p -> PaymentView.of(p, planNames.get(p.getSubscriptionId()))).toList();
    }

    private Subscription findSubscription(UUID id) {
        return subscriptions.findById(id).orElseThrow(() -> new NotFoundException("subscription", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
