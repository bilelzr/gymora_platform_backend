package com.gymora.subscription.api;

import com.gymora.subscription.SubscriptionCapabilities;
import com.gymora.subscription.application.PlanService;
import com.gymora.subscription.application.SubscriptionViews.PlanView;
import com.gymora.subscription.domain.DurationUnit;
import com.gymora.subscription.domain.Plan.PlanDetails;
import com.gymora.subscription.domain.PlanStatus;
import com.gymora.security.capability.RequiresCapability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Subscription · Plans", description = "What the gym sells: length, price and freeze rules.")
public class PlanController {

    private final PlanService service;

    public PlanController(PlanService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresCapability(SubscriptionCapabilities.PLAN_READ)
    @Operation(summary = "Plans by name, active ones by default, with how many subscriptions run today")
    public List<PlanView> list(@RequestParam(required = false) PlanStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    @RequiresCapability(SubscriptionCapabilities.PLAN_READ)
    public PlanView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @RequiresCapability(SubscriptionCapabilities.PLAN_MANAGE)
    public ResponseEntity<PlanView> create(@Valid @RequestBody PlanRequest request) {
        PlanView created = service.create(request.toDetails());
        return ResponseEntity.created(URI.create("/api/v1/plans/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @RequiresCapability(SubscriptionCapabilities.PLAN_MANAGE)
    @Operation(summary = "Change a plan", description = "Applies to future sales only; subscriptions already sold keep their terms.")
    public PlanView update(@PathVariable UUID id, @Valid @RequestBody PlanRequest request) {
        return service.update(id, request.toDetails());
    }

    @PostMapping("/{id}/archive")
    @RequiresCapability(SubscriptionCapabilities.PLAN_MANAGE)
    @Operation(summary = "Stop selling a plan; subscriptions already sold are not affected")
    public PlanView archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    @PostMapping("/{id}/reactivate")
    @RequiresCapability(SubscriptionCapabilities.PLAN_MANAGE)
    public PlanView reactivate(@PathVariable UUID id) {
        return service.reactivate(id);
    }

    public record PlanRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @NotNull @Min(1) @Max(1000) Integer durationValue,
            @NotNull DurationUnit durationUnit,
            @NotNull @Min(0) @Max(100_000_000) Long priceMillimes,
            @Min(1) @Max(1000) Integer sessionCount,
            @NotNull @Min(0) @Max(365) Integer maxFreezeDays) {

        PlanDetails toDetails() {
            String desc = description == null || description.isBlank() ? null : description.trim();
            return new PlanDetails(name.trim(), desc, durationValue, durationUnit, priceMillimes, sessionCount,
                    maxFreezeDays);
        }
    }
}
