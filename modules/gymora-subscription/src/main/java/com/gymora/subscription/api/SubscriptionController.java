package com.gymora.subscription.api;

import com.gymora.common.web.PageResponse;
import com.gymora.subscription.SubscriptionCapabilities;
import com.gymora.subscription.application.SubscriptionService;
import com.gymora.subscription.application.SubscriptionService.Sale;
import com.gymora.subscription.application.SubscriptionService.SubscriptionFilter;
import com.gymora.subscription.application.SubscriptionViews.SaleView;
import com.gymora.subscription.application.SubscriptionViews.SubscriptionCounts;
import com.gymora.subscription.application.SubscriptionViews.SubscriptionRow;
import com.gymora.subscription.application.SubscriptionViews.SubscriptionView;
import com.gymora.security.capability.RequiresCapability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Subscription · Subscriptions", description = "Plans sold to members: sales, renewals, freezes, cancellations.")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/members/{memberId}/subscriptions")
    @RequiresCapability(SubscriptionCapabilities.SUBSCRIPTION_READ)
    @Operation(summary = "A member's subscriptions, latest first",
            description = "Amounts are only included for callers who can see payments.")
    public List<SubscriptionView> forMember(@PathVariable UUID memberId) {
        return service.forMember(memberId);
    }

    @GetMapping("/api/v1/members/{memberId}/subscriptions/next-start")
    @RequiresCapability(SubscriptionCapabilities.SUBSCRIPTION_SELL)
    @Operation(summary = "Where a new subscription starts by default: the day after the current one ends, or today")
    public Map<String, LocalDate> nextStart(@PathVariable UUID memberId) {
        return Map.of("startDate", service.nextStart(memberId));
    }

    @PostMapping("/api/v1/members/{memberId}/subscriptions")
    @RequiresCapability(SubscriptionCapabilities.SUBSCRIPTION_SELL)
    @Operation(summary = "Sell a plan to a member (or renew)",
            description = "Without startDate, starts the day after the current subscription ends, or today. "
                    + "paymentMillimes > 0 also records a cash payment.")
    public ResponseEntity<SaleView> sell(@PathVariable UUID memberId, @Valid @RequestBody SaleRequest request) {
        SaleView sale = service.sell(memberId, new Sale(request.planId(), request.startDate(),
                request.discountMillimes(), request.paymentMillimes(), request.note()));
        return ResponseEntity.status(201).body(sale);
    }

    @GetMapping("/api/v1/subscriptions")
    @RequiresCapability(SubscriptionCapabilities.SUBSCRIPTION_READ)
    @Operation(summary = "Follow-up lists: active, expiring within 7 days, expired within 30 days, frozen, unpaid, upcoming",
            description = "UNPAID needs subscription.payment.read. q narrows to members matching a name, number or phone.")
    public PageResponse<SubscriptionRow> search(@RequestParam(defaultValue = "ACTIVE") SubscriptionFilter view,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(service.search(view, q, page, size), row -> row);
    }

    @GetMapping("/api/v1/subscriptions/counts")
    @RequiresCapability(SubscriptionCapabilities.SUBSCRIPTION_READ)
    @Operation(summary = "How many subscriptions are in each follow-up list")
    public SubscriptionCounts counts() {
        return service.counts();
    }

    @GetMapping("/api/v1/subscriptions/{id}")
    @RequiresCapability(SubscriptionCapabilities.SUBSCRIPTION_READ)
    public SubscriptionView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/api/v1/subscriptions/{id}/freeze")
    @RequiresCapability(SubscriptionCapabilities.SUBSCRIPTION_FREEZE)
    @Operation(summary = "Freeze between two days (both included); the end date moves by as many days")
    public SubscriptionView freeze(@PathVariable UUID id, @Valid @RequestBody FreezeRequest request) {
        return service.freeze(id, request.startDate(), request.endDate(), request.reason().trim());
    }

    @PostMapping("/api/v1/subscriptions/{id}/unfreeze")
    @RequiresCapability(SubscriptionCapabilities.SUBSCRIPTION_FREEZE)
    @Operation(summary = "End the current or planned freeze: the member is back today; unused days are given back")
    public SubscriptionView unfreeze(@PathVariable UUID id) {
        return service.unfreeze(id);
    }

    @PostMapping("/api/v1/subscriptions/{id}/cancel")
    @RequiresCapability(SubscriptionCapabilities.SUBSCRIPTION_CANCEL)
    @Operation(summary = "Cancel a subscription today", description = "Payments stay in the history; refunds happen outside GYMORA.")
    public SubscriptionView cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
        return service.cancel(id, request.reason().trim());
    }

    public record SaleRequest(
            @NotNull UUID planId,
            LocalDate startDate,
            @Min(0) Long discountMillimes,
            @Min(0) Long paymentMillimes,
            @Size(max = 300) String note) {
    }

    public record FreezeRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                @NotBlank @Size(max = 300) String reason) {
    }

    public record CancelRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
