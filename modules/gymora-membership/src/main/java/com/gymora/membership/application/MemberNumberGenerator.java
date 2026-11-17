package com.gymora.membership.application;

import com.gymora.common.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hands out member numbers per gym: M-000001, M-000002, … The counter row is incremented
 * atomically by PostgreSQL inside the caller's transaction, so two receptionists registering at
 * the same moment never get the same number, and a failed registration doesn't use one up.
 */
@Component
public class MemberNumberGenerator {

    private final JdbcTemplate jdbc;

    public MemberNumberGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String next() {
        Long value = jdbc.queryForObject("""
                insert into membership_member_counter (tenant_id, last_value) values (?, 1)
                on conflict (tenant_id) do update set last_value = membership_member_counter.last_value + 1
                returning last_value""", Long.class, TenantContext.require());
        return "M-%06d".formatted(value);
    }
}
