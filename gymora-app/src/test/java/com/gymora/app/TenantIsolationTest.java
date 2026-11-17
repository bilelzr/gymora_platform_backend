package com.gymora.app;

import static com.gymora.app.support.Tokens.GYM_A;
import static com.gymora.app.support.Tokens.GYM_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.gymora.app.probe.ProbeEntity;
import com.gymora.app.probe.ProbeRepository;
import com.gymora.app.support.GymoraIntegrationTest;
import com.gymora.common.tenant.MissingTenantException;
import com.gymora.common.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@GymoraIntegrationTest
class TenantIsolationTest {

    @Autowired
    ProbeRepository probes;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from test_probe");
        TenantContext.clear();
    }

    @Test
    void recordsAreStampedWithTheCurrentGymAndAudited() {
        ProbeEntity saved = TenantContext.callAs(GYM_A, () -> probes.save(new ProbeEntity("front desk")));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTenantId()).isEqualTo(GYM_A);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("system");
    }

    @Test
    void anotherGymCanNeitherListNorFindTheRecord() {
        UUID idInA = TenantContext.callAs(GYM_A, () -> probes.save(new ProbeEntity("gym A only")).getId());

        TenantContext.runAs(GYM_B, () -> {
            assertThat(probes.findAll()).isEmpty();
            assertThat(probes.count()).isZero();
            assertThat(probes.findById(idInA)).isEmpty();
            assertThat(probes.existsById(idInA)).isFalse();
        });

        TenantContext.runAs(GYM_A, () -> assertThat(probes.findById(idInA)).isPresent());
    }

    @Test
    void withoutATenantNothingIsVisible() {
        TenantContext.callAs(GYM_A, () -> probes.save(new ProbeEntity("hidden")));

        assertThat(probes.findAll()).isEmpty();
    }

    @Test
    void savingGymDataWithoutATenantIsRefused() {
        Throwable thrown = catchThrowable(() -> probes.save(new ProbeEntity("orphan")));

        assertThat(thrown).isNotNull();
        assertThat(causeChainContains(thrown, MissingTenantException.class)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from test_probe", Long.class)).isZero();
    }

    @Test
    void theDatabaseRejectsTheNoTenantId() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into test_probe (id, tenant_id, name, version, created_at, updated_at)
                values (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'x', 0, now(), now())"""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static boolean causeChainContains(Throwable thrown, Class<? extends Throwable> type) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
