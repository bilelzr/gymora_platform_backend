package com.gymora.subscription.domain;

import java.time.LocalDate;

public enum DurationUnit {
    DAY,
    MONTH;

    /**
     * Last day (inclusive) of a period of {@code value} units starting on {@code start}, so the next
     * period starts the day after.
     *
     * <p>Months follow the calendar: 1 Jan + 1 month ends 31 Jan, 15 Jan ends 14 Feb. When the
     * start day doesn't exist in the target month (31 Jan + 1 month), the period runs to that
     * month's last day (28 or 29 Feb) rather than stopping a day short.
     */
    public LocalDate lastDay(LocalDate start, int value) {
        if (this == DAY) {
            return start.plusDays(value - 1L);
        }
        LocalDate sameDay = start.plusMonths(value);
        return sameDay.getDayOfMonth() < start.getDayOfMonth() ? sameDay : sameDay.minusDays(1);
    }
}
