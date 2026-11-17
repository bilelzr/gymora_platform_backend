package com.gymora.common.time;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One clock for the whole application, in the gyms' business time zone (Africa/Tunis by default).
 * Use it for "today" (membership start and end dates); timestamps are stored as UTC instants.
 * Tests replace it with a fixed clock.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock(@Value("${gymora.time-zone:Africa/Tunis}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
