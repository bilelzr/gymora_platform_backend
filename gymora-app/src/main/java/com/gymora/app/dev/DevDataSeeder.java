package com.gymora.app.dev;

import com.gymora.access.application.DefaultProfileSeeder;
import com.gymora.access.domain.ProfileRepository;
import com.gymora.access.domain.StaffMember;
import com.gymora.access.domain.StaffRepository;
import com.gymora.common.tenant.TenantContext;
import com.gymora.security.capability.DefaultProfileGrants.DefaultProfile;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Local development data matching the users in infra/keycloak/gymora-realm.json. Runs only with
 * the {@code dev} profile and is safe to run on every start.
 *
 * <ul>
 *   <li>Gym A (owner.a) and gym B (owner.b), both active, with their starting profiles.</li>
 *   <li>staff.a works at gym A as a Receptionist.</li>
 * </ul>
 */
@Component
@Profile("dev")
class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    static final UUID GYM_A = UUID.fromString("a1a1a1a1-0000-4000-8000-00000000000a");
    static final UUID GYM_B = UUID.fromString("b2b2b2b2-0000-4000-8000-00000000000b");
    static final String OWNER_A = "0a000000-0000-4000-8000-0000000000a1";
    static final String OWNER_B = "0b000000-0000-4000-8000-0000000000b1";
    static final String STAFF_A = "0a000000-0000-4000-8000-0000000000a2";

    private final JdbcTemplate jdbc;
    private final DefaultProfileSeeder profileSeeder;
    private final ProfileRepository profiles;
    private final StaffRepository staff;
    private final TransactionTemplate tx;

    DevDataSeeder(JdbcTemplate jdbc, DefaultProfileSeeder profileSeeder, ProfileRepository profiles,
                  StaffRepository staff, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.profileSeeder = profileSeeder;
        this.profiles = profiles;
        this.staff = staff;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        insertGym(GYM_A, "GYMORA Demo Lac 2", "Tunis", OWNER_A, "owner.a@gymora.local", "Amira", "Owner A");
        insertGym(GYM_B, "GYMORA Demo Sousse", "Sousse", OWNER_B, "owner.b@gymora.local", "Bassem", "Owner B");
        TenantContext.runAs(GYM_A, profileSeeder::seedCurrentGym);
        TenantContext.runAs(GYM_B, profileSeeder::seedCurrentGym);
        TenantContext.runAs(GYM_A, () -> tx.executeWithoutResult(status -> {
            if (staff.findByUserId(STAFF_A).isEmpty()) {
                var receptionist = profiles.findAllByOrderByNameAsc().stream()
                        .filter(p -> p.getDefaultKey() == DefaultProfile.RECEPTIONIST)
                        .toList();
                staff.save(new StaffMember(STAFF_A, "staff.a@gymora.local", "Sami", "Staff A", null, receptionist));
            }
        }));
        log.info("Dev data ready: gyms A and B, staff.a is a Receptionist at gym A");
    }

    private void insertGym(UUID id, String name, String city, String ownerId, String ownerEmail,
                           String ownerFirst, String ownerLast) {
        jdbc.update("""
                insert into tenant_gym (id, name, city, country_code, currency, time_zone, status,
                    owner_user_id, owner_email, owner_first_name, owner_last_name,
                    version, created_at, created_by, updated_at, updated_by)
                values (?, ?, ?, 'TN', 'TND', 'Africa/Tunis', 'ACTIVE', ?, ?, ?, ?, 0, now(), 'dev-seed', now(), 'dev-seed')
                on conflict (id) do nothing""",
                id, name, city, ownerId, ownerEmail, ownerFirst, ownerLast);
    }

}
