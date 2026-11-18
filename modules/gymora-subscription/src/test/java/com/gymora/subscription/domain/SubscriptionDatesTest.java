package com.gymora.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SubscriptionDatesTest {

    @Test
    void monthsFollowTheCalendar() {
        assertThat(DurationUnit.MONTH.lastDay(LocalDate.of(2026, 1, 1), 1)).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(DurationUnit.MONTH.lastDay(LocalDate.of(2026, 1, 15), 1)).isEqualTo(LocalDate.of(2026, 2, 14));
        assertThat(DurationUnit.MONTH.lastDay(LocalDate.of(2026, 10, 1), 3)).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(DurationUnit.MONTH.lastDay(LocalDate.of(2026, 11, 18), 12)).isEqualTo(LocalDate.of(2027, 11, 17));
    }

    @Test
    void aStartDayMissingFromTheTargetMonthRunsToThatMonthsEnd() {
        assertThat(DurationUnit.MONTH.lastDay(LocalDate.of(2026, 1, 31), 1)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(DurationUnit.MONTH.lastDay(LocalDate.of(2028, 1, 31), 1)).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(DurationUnit.MONTH.lastDay(LocalDate.of(2026, 3, 31), 1)).isEqualTo(LocalDate.of(2026, 4, 30));
        // The next period then starts on the 1st: no day is lost or counted twice.
        assertThat(DurationUnit.MONTH.lastDay(LocalDate.of(2026, 1, 28), 1)).isEqualTo(LocalDate.of(2026, 2, 27));
    }

    @Test
    void daysCountTheFirstDay() {
        assertThat(DurationUnit.DAY.lastDay(LocalDate.of(2026, 11, 18), 1)).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(DurationUnit.DAY.lastDay(LocalDate.of(2026, 11, 18), 30)).isEqualTo(LocalDate.of(2026, 12, 17));
    }

    @Test
    void freezesCountBothEnds() {
        assertThat(Freeze.daysBetween(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 27))).isEqualTo(10);
        assertThat(Freeze.daysBetween(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 18))).isEqualTo(1);
    }
}
