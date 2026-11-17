package com.gymora.app;

import static com.gymora.app.support.Tokens.GYM_A;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymora.app.probe.ProbeController;
import com.gymora.app.support.GymoraIntegrationTest;
import com.gymora.app.support.Tokens;
import com.gymora.security.capability.CapabilityProvider;
import com.gymora.security.jwt.GymoraAuthoritiesConverter;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@GymoraIntegrationTest
@Import(SecurityAndTenancyTest.FakeProfiles.class)
class SecurityAndTenancyTest {

    static final String STAFF_WITH_CAPABILITY = "staff-receptionist";
    static final String STAFF_WITHOUT_CAPABILITY = "staff-trainer";

    /** Stands in for the access module: one staff user holds the probe capability. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FakeProfiles {
        @Bean
        @Primary
        CapabilityProvider fakeCapabilityProvider() {
            return (userId, tenantId) -> STAFF_WITH_CAPABILITY.equals(userId)
                    ? Set.of(ProbeController.CAPABILITY)
                    : Set.of();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    GymoraAuthoritiesConverter converter;

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void apiNeedsAToken() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void ownerSeesTheirGymAndEveryCapability() throws Exception {
        mvc.perform(get("/api/v1/me").with(Tokens.owner(GYM_A, converter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(GYM_A.toString()))
                .andExpect(jsonPath("$.gym.name").value("Test Gym A"))
                .andExpect(jsonPath("$.gym.currency").value("TND"))
                .andExpect(jsonPath("$.roles", contains("GYM_OWNER")))
                .andExpect(jsonPath("$.capabilities", contains("*")));
    }

    @Test
    void staffWithoutAGymAreRefused() throws Exception {
        mvc.perform(get("/api/v1/me").with(Tokens.staff("lost-user", null, converter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("error.tenant_required"));
    }

    @Test
    void anInvalidTenantClaimIsRefused() throws Exception {
        mvc.perform(get("/api/v1/me").with(Tokens.token("someone", "not-a-uuid", List.of(), converter)))
                .andExpect(status().isForbidden());
    }

    @Test
    void errorsFollowTheCallersLanguage() throws Exception {
        mvc.perform(get("/api/v1/me").header("Accept-Language", "ar").with(Tokens.staff("lost-user", null, converter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("قاعة")));
        mvc.perform(get("/api/v1/me").with(Tokens.staff("lost-user", null, converter)))
                .andExpect(jsonPath("$.detail", containsString("salle")));
    }

    @Test
    void platformAdminHasNoGymEvenWithATenantClaim() throws Exception {
        mvc.perform(get("/api/v1/me").with(Tokens.token("admin", GYM_A.toString(), List.of("PLATFORM_ADMIN"), converter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(nullValue()));
    }

    @Test
    void ownerPassesEveryCapability() throws Exception {
        mvc.perform(get("/api/v1/test-probes").with(Tokens.owner(GYM_A, converter)))
                .andExpect(status().isOk());
    }

    @Test
    void staffPassOnlyWithTheCapability() throws Exception {
        mvc.perform(get("/api/v1/test-probes").with(Tokens.staff(STAFF_WITH_CAPABILITY, GYM_A, converter)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/test-probes").with(Tokens.staff(STAFF_WITHOUT_CAPABILITY, GYM_A, converter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("error.access_denied"));
    }

    @Test
    void staffCapabilitiesAppearInMe() throws Exception {
        mvc.perform(get("/api/v1/me").with(Tokens.staff(STAFF_WITH_CAPABILITY, GYM_A, converter)))
                .andExpect(jsonPath("$.capabilities", contains(ProbeController.CAPABILITY)));
    }

    @Test
    void platformAdminCannotUseGymCapabilities() throws Exception {
        mvc.perform(get("/api/v1/test-probes").with(Tokens.platformAdmin(converter)))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersOfASuspendedGymAreRefused() throws Exception {
        mvc.perform(get("/api/v1/me").with(Tokens.owner(Tokens.GYM_C, converter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("error.tenant_suspended"));
    }

    @Test
    void usersOfAnUnknownGymAreRefused() throws Exception {
        mvc.perform(get("/api/v1/me").with(Tokens.owner(java.util.UUID.randomUUID(), converter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("error.tenant_unknown"));
    }

    @Test
    void platformEndpointsAreForPlatformAdminsOnly() throws Exception {
        mvc.perform(get("/api/v1/platform/gyms").with(Tokens.owner(GYM_A, converter)))
                .andExpect(status().isForbidden());
    }
}
