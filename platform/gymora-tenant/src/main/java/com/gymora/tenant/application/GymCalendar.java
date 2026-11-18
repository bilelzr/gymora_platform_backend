package com.gymora.tenant.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gymora.common.tenant.TenantContext;
import com.gymora.tenant.domain.GymRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * "Today" for the current gym, in the gym's own time zone. Subscriptions start and end on gym days:
 * at 00:30 in Tunis it is already tomorrow for the gym, whatever the server's clock says.
 */
@Component
public class GymCalendar {

    private final GymRepository gyms;
    private final Clock clock;
    private final Cache<UUID, ZoneId> zones = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    public GymCalendar(GymRepository gyms, Clock clock) {
        this.gyms = gyms;
        this.clock = clock;
    }

    public ZoneId zone() {
        return zones.get(TenantContext.require(), id -> gyms.findById(id)
                .map(gym -> ZoneId.of(gym.getTimeZone()))
                .orElse(clock.getZone()));
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone());
    }

    public LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, zone());
    }

    public Instant now() {
        return clock.instant();
    }
}
