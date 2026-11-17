package com.gymora.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymora.access.domain.Profile;
import com.gymora.access.domain.ProfileRepository;
import com.gymora.app.support.GymoraIntegrationTest;
import com.gymora.app.support.RecordingIdentityAdmin;
import com.gymora.app.support.Tokens;
import com.gymora.common.tenant.TenantContext;
import com.gymora.security.capability.DefaultProfileGrants.DefaultProfile;
import com.gymora.security.jwt.GymoraAuthoritiesConverter;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@GymoraIntegrationTest
class PlatformGymsTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    GymoraAuthoritiesConverter converter;

    @Autowired
    RecordingIdentityAdmin identity;

    @Autowired
    ProfileRepository profiles;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        identity.reset();
    }

    @Test
    void creatingAGymCreatesItsOwnerAccountAndStartingProfiles() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@club.tn";
        String body = mvc.perform(post("/api/v1/platform/gyms")
                        .with(Tokens.platformAdmin(converter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGymJson("Club Olympia", email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currency").value("TND"))
                .andExpect(jsonPath("$.owner.email").value(email))
                .andReturn().getResponse().getContentAsString();

        UUID gymId = UUID.fromString(JsonPath.read(body, "$.id"));
        String ownerId = JsonPath.read(body, "$.owner.userId");

        assertThat(identity.calls).containsExactly(
                "createUser:" + email + ":" + gymId,
                "grantRealmRole:" + ownerId + ":GYM_OWNER",
                "sendSetupEmail:" + ownerId);

        List<Profile> seeded = TenantContext.callAs(gymId, () -> profiles.findAll());
        assertThat(seeded).extracting(Profile::getName)
                .containsExactlyInAnyOrder("Coach", "Réceptionniste", "Responsable");
        assertThat(seeded).allSatisfy(profile -> assertThat(profile.getTenantId()).isEqualTo(gymId));
        assertThat(seeded).filteredOn(profile -> profile.getDefaultKey() == DefaultProfile.MANAGER)
                .singleElement()
                .satisfies(manager -> assertThat(manager.getCapabilities()).contains("access.staff.manage"));

        // The new owner can work in their gym straight away; names are sorted the French way.
        mvc.perform(get("/api/v1/profiles").with(Tokens.owner(gymId, converter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Coach", "Réceptionniste", "Responsable")));
    }

    @Test
    void anEmailAlreadyInKeycloakCreatesNothing() throws Exception {
        String email = "taken-" + UUID.randomUUID() + "@club.tn";
        identity.takenEmails.add(email);

        mvc.perform(post("/api/v1/platform/gyms")
                        .with(Tokens.platformAdmin(converter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGymJson("Club Taken", email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.identity_email_taken"));

        assertThat(jdbc.queryForObject("select count(*) from tenant_gym where owner_email = ?", Long.class, email))
                .isZero();
    }

    @Test
    void suspendingAGymLocksOutItsUsersUntilReactivated() throws Exception {
        String body = mvc.perform(post("/api/v1/platform/gyms")
                        .with(Tokens.platformAdmin(converter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGymJson("Club Suspend", "s-" + UUID.randomUUID() + "@club.tn")))
                .andReturn().getResponse().getContentAsString();
        UUID gymId = UUID.fromString(JsonPath.read(body, "$.id"));

        mvc.perform(get("/api/v1/me").with(Tokens.owner(gymId, converter))).andExpect(status().isOk());

        mvc.perform(post("/api/v1/platform/gyms/{id}/suspend", gymId)
                        .with(Tokens.platformAdmin(converter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Subscription unpaid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/v1/me").with(Tokens.owner(gymId, converter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("error.tenant_suspended"));

        mvc.perform(post("/api/v1/platform/gyms/{id}/activate", gymId).with(Tokens.platformAdmin(converter)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").with(Tokens.owner(gymId, converter))).andExpect(status().isOk());
    }

    @Test
    void gymsCanBeSearchedByName() throws Exception {
        String name = "Club Search " + UUID.randomUUID();
        mvc.perform(post("/api/v1/platform/gyms")
                .with(Tokens.platformAdmin(converter))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createGymJson(name, "q-" + UUID.randomUUID() + "@club.tn")));

        mvc.perform(get("/api/v1/platform/gyms").param("q", name).with(Tokens.platformAdmin(converter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[*].name", hasItem(name)));
    }

    @Test
    void invalidRequestsListTheBadFields() throws Exception {
        mvc.perform(post("/api/v1/platform/gyms")
                        .with(Tokens.platformAdmin(converter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gym\":{\"name\":\"\"},\"owner\":{\"email\":\"not-an-email\",\"firstName\":\"A\",\"lastName\":\"B\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("error.validation"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("gym.name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("owner.email")));
    }

    private static String createGymJson(String name, String ownerEmail) {
        return """
                {"gym": {"name": "%s", "city": "Tunis", "phone": "+216 71 000 000"},
                 "owner": {"email": "%s", "firstName": "Leila", "lastName": "Ben Salah"}}"""
                .formatted(name, ownerEmail);
    }
}
