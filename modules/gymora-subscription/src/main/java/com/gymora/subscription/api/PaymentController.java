package com.gymora.subscription.api;

import com.gymora.subscription.SubscriptionCapabilities;
import com.gymora.subscription.application.PaymentService;
import com.gymora.subscription.application.SubscriptionViews.DayCash;
import com.gymora.subscription.application.SubscriptionViews.PaymentView;
import com.gymora.security.capability.RequiresCapability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Subscription · Payments", description = "Cash received for subscriptions, and the day's cash.")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/subscriptions/{id}/payments")
    @RequiresCapability(SubscriptionCapabilities.PAYMENT_READ)
    public List<PaymentView> forSubscription(@PathVariable UUID id) {
        return service.forSubscription(id);
    }

    @GetMapping("/api/v1/members/{memberId}/payments")
    @RequiresCapability(SubscriptionCapabilities.PAYMENT_READ)
    @Operation(summary = "All payments of a member, latest first, voided ones included")
    public List<PaymentView> forMember(@PathVariable UUID memberId) {
        return service.forMember(memberId);
    }

    @PostMapping("/api/v1/subscriptions/{id}/payments")
    @RequiresCapability(SubscriptionCapabilities.PAYMENT_RECORD)
    @Operation(summary = "Record cash received", description = "At most what remains to pay. Gives the gym's next receipt number (R-000123).")
    public ResponseEntity<PaymentView> record(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(201).body(service.record(id, request.amountMillimes(), request.note()));
    }

    @PostMapping("/api/v1/payments/{id}/void")
    @RequiresCapability(SubscriptionCapabilities.PAYMENT_VOID)
    @Operation(summary = "Void a payment recorded by mistake; it stays in the history, crossed out")
    public PaymentView voidPayment(@PathVariable UUID id, @Valid @RequestBody VoidRequest request) {
        return service.voidPayment(id, request.reason().trim());
    }

    @GetMapping("/api/v1/payments/day")
    @RequiresCapability(SubscriptionCapabilities.PAYMENT_READ)
    @Operation(summary = "Cash received on a gym day (today by default), with totals per staff member")
    public DayCash day(@RequestParam(required = false) LocalDate date) {
        return service.day(date);
    }

    public record PaymentRequest(@NotNull @Min(1) Long amountMillimes, @Size(max = 300) String note) {
    }

    public record VoidRequest(@NotBlank @Size(max = 300) String reason) {
    }
}
