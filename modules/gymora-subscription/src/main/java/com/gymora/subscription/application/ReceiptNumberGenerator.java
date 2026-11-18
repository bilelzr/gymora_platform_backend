package com.gymora.subscription.application;

import com.gymora.common.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hands out receipt numbers per gym: R-000001, R-000002, … Incremented atomically by PostgreSQL
 * inside the caller's transaction, so a failed payment doesn't use a number up.
 */
@Component
public class ReceiptNumberGenerator {

    private final JdbcTemplate jdbc;

    public ReceiptNumberGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String next() {
        Long value = jdbc.queryForObject("""
                insert into subscription_receipt_counter (tenant_id, last_value) values (?, 1)
                on conflict (tenant_id) do update set last_value = subscription_receipt_counter.last_value + 1
                returning last_value""", Long.class, TenantContext.require());
        return "R-%06d".formatted(value);
    }
}
