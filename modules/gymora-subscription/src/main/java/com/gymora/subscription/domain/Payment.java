package com.gymora.subscription.domain;

import com.gymora.common.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Money received for a subscription. Never deleted: a mistake is voided, with a reason. */
@Entity
@Table(name = "subscription_payment")
public class Payment extends TenantScopedEntity {

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Column(name = "receipt_number", nullable = false, updatable = false, length = 20)
    private String receiptNumber;

    @Column(name = "amount_millimes", nullable = false, updatable = false)
    private long amountMillimes;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    @Column(name = "paid_on", nullable = false, updatable = false)
    private LocalDate paidOn;

    @Column(name = "received_by_name", nullable = false, updatable = false, length = 150)
    private String receivedByName;

    @Column(name = "note", updatable = false, length = 300)
    private String note;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by_name", length = 150)
    private String voidedByName;

    @Column(name = "void_reason", length = 300)
    private String voidReason;

    protected Payment() {
    }

    public Payment(Subscription subscription, String receiptNumber, long amountMillimes, PaymentMethod method,
                   Instant paidAt, LocalDate paidOn, String receivedByName, String note) {
        this.subscriptionId = subscription.getId();
        this.memberId = subscription.getMemberId();
        this.receiptNumber = receiptNumber;
        this.amountMillimes = amountMillimes;
        this.method = method;
        this.paidAt = paidAt;
        this.paidOn = paidOn;
        this.receivedByName = receivedByName;
        this.note = note;
    }

    public void voidIt(Instant at, String byName, String reason) {
        this.voidedAt = at;
        this.voidedByName = byName;
        this.voidReason = reason;
    }

    public boolean isVoided() {
        return voidedAt != null;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public long getAmountMillimes() {
        return amountMillimes;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public String getReceivedByName() {
        return receivedByName;
    }

    public String getNote() {
        return note;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public String getVoidedByName() {
        return voidedByName;
    }

    public String getVoidReason() {
        return voidReason;
    }
}
