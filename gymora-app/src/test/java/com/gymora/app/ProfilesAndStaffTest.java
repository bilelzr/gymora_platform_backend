package com.gymora.app;

import static com.gymora.app.support.Tokens.GYM_A;
import static com.gymora.app.support.Tokens.GYM_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymora.app.support.GymoraIntegrationTest;
import com.gymora.app.support.RecordingIdentityAdmin;
import com.gymora.app.support.Tokens;
import com.gymora.security.jwt.GymoraAuthoritiesConverter;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@GymoraIntegrationTest
class ProfilesAndStaffTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    GymoraAuthoritiesConverter converter;

    @Autowired
    RecordingIdentityAdmin identity;

    RequestPostProcessor ownerA;
    RequestPostProcessor ownerB;

    @BeforeEach
    void setUp() {
        identity.reset();
        ownerA = Tokens.owner(GYM_A, converter);
        ownerB = Tokens.owner(GYM_B, converter);
    }

    // ---------- capabilities catalog ----------

    @Test
    void theCatalogListsEveryEndpointCapabilityWithLabelsInTheCallersLanguage() throws Exception {
        mvc.perform(get("/api/v1/capabilities").with(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.module=='access')].label", contains("Accès et équipe")))
                .andExpect(jsonPath("$[?(@.module=='access')].capabilities[*].key",
                        hasItem("access.staff.manage")));
        mvc.perform(get("/api/v1/capabilities").header("Accept-Language", "en").with(ownerA))
                .andExpect(jsonPath("$[?(@.module=='access')].label", contains("Access and staff")));
    }

    // ---------- profiles ----------

    @Test
    void ownerCreatesProfilesWithKnownCapabilitiesAndUniqueNames() throws Exception {
        String name = unique("Front desk");
        createProfile(ownerA, name, "access.staff.read");

        mvc.perform(json(post("/api/v1/profiles"), "{\"name\":\"%s\",\"capabilities\":[]}".formatted(name.toUpperCase()))
                        .with(ownerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.profile_name_taken"));

        mvc.perform(json(post("/api/v1/profiles"), "{\"name\":\"%s\",\"capabilities\":[\"members.fly\"]}"
                        .formatted(unique("Bad"))).with(ownerA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("error.capability_unknown"));
    }

    @Test
    void anotherGymCannotSeeOrUseAProfile() throws Exception {
        String profileId = createProfile(ownerA, unique("Gym A only"), "access.staff.read");

        mvc.perform(get("/api/v1/profiles/{id}", profileId).with(ownerB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/profiles").with(ownerB))
                .andExpect(jsonPath("$[*].id", not(hasItem(profileId))));
        mvc.perform(json(post("/api/v1/staff"), staffJson(unique("b") + "@gym-b.tn", profileId)).with(ownerB))
                .andExpect(status().isNotFound());
        assertThat(identity.calls).isEmpty();
    }

    @Test
    void aProfileHeldByStaffCannotBeDeleted() throws Exception {
        String profileId = createProfile(ownerA, unique("In use"), "access.staff.read");
        String staffId = JsonPath.read(createStaff(ownerA, profileId), "$.id");

        mvc.perform(delete("/api/v1/profiles/{id}", profileId).with(ownerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.profile_in_use"));

        mvc.perform(json(put("/api/v1/staff/{id}/profiles", staffId), "{\"profileIds\":[]}").with(ownerA))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/profiles/{id}", profileId).with(ownerA)).andExpect(status().isNoContent());
    }

    // ---------- staff ----------

    @Test
    void newStaffGetAnAccountInTheirGymAndOnlyTheirProfilesCapabilities() throws Exception {
        String profileId = createProfile(ownerA, unique("Reader"), "access.staff.read");
        String email = unique("sami") + "@gym-a.tn";
        String body = createStaff(ownerA, profileId, email);
        String userId = JsonPath.read(body, "$.userId");

        assertThat(identity.calls).containsExactly("createUser:" + email + ":" + GYM_A, "sendSetupEmail:" + userId);

        RequestPostProcessor staff = Tokens.staff(userId, GYM_A, converter);
        mvc.perform(get("/api/v1/staff").with(staff)).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/staff"), staffJson(unique("x") + "@gym-a.tn")).with(staff))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me").with(staff))
                .andExpect(jsonPath("$.capabilities", contains("access.staff.read")))
                .andExpect(jsonPath("$.profiles[0].id").value(profileId));
    }

    @Test
    void anEmailCanOnlyBeUsedOncePerGym() throws Exception {
        String email = unique("dup") + "@gym-a.tn";
        createStaff(ownerA, null, email);
        mvc.perform(json(post("/api/v1/staff"), staffJson(email)).with(ownerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.staff_email_taken"));
    }

    @Test
    void capabilityChangesApplyOnTheNextRequest() throws Exception {
        String profileId = createProfile(ownerA, unique("Changing"), "access.staff.read");
        String userId = JsonPath.read(createStaff(ownerA, profileId), "$.userId");
        RequestPostProcessor staff = Tokens.staff(userId, GYM_A, converter);
        mvc.perform(get("/api/v1/staff").with(staff)).andExpect(status().isOk());

        mvc.perform(json(put("/api/v1/profiles/{id}/capabilities", profileId), "{\"capabilities\":[]}").with(ownerA))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/staff").with(Tokens.staff(userId, GYM_A, converter)))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledStaffLoseTheirAccountAndCapabilities() throws Exception {
        String profileId = createProfile(ownerA, unique("Disable"), "access.staff.read");
        String body = createStaff(ownerA, profileId);
        String staffId = JsonPath.read(body, "$.id");
        String userId = JsonPath.read(body, "$.userId");

        mvc.perform(post("/api/v1/staff/{id}/disable", staffId).with(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
        assertThat(identity.calls).contains("setEnabled:" + userId + ":false");

        mvc.perform(get("/api/v1/staff").with(Tokens.staff(userId, GYM_A, converter)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me").with(Tokens.staff(userId, GYM_A, converter)))
                .andExpect(jsonPath("$.capabilities", empty()));
    }

    // ---------- no privilege escalation ----------

    @Test
    void staffCanOnlyGrantCapabilitiesTheyHold() throws Exception {
        String profileId = createProfile(ownerA, unique("Profile admin"), "access.profile.read", "access.profile.manage");
        String userId = JsonPath.read(createStaff(ownerA, profileId), "$.userId");
        RequestPostProcessor staff = Tokens.staff(userId, GYM_A, converter);

        mvc.perform(json(post("/api/v1/profiles"),
                        "{\"name\":\"%s\",\"capabilities\":[\"access.staff.manage\"]}".formatted(unique("Power")))
                        .with(staff))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("error.access_escalation"));

        mvc.perform(json(post("/api/v1/profiles"),
                        "{\"name\":\"%s\",\"capabilities\":[\"access.profile.read\"]}".formatted(unique("Weaker")))
                        .with(staff))
                .andExpect(status().isCreated());

        // A more powerful profile can't be edited either.
        String powerful = createProfile(ownerA, unique("Powerful"), "access.staff.manage");
        mvc.perform(json(put("/api/v1/profiles/{id}/capabilities", powerful), "{\"capabilities\":[]}").with(staff))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("error.access_escalation"));
    }

    @Test
    void staffCannotManageTheirOwnAccess() throws Exception {
        String profileId = createProfile(ownerA, unique("Staff admin"), "access.staff.read", "access.staff.manage");
        String body = createStaff(ownerA, profileId);
        String staffId = JsonPath.read(body, "$.id");
        RequestPostProcessor self = Tokens.staff(JsonPath.read(body, "$.userId"), GYM_A, converter);

        mvc.perform(post("/api/v1/staff/{id}/disable", staffId).with(self))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("error.access_self"));
        mvc.perform(json(put("/api/v1/staff/{id}/profiles", staffId), "{\"profileIds\":[]}").with(self))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("error.access_self"));
    }

    @Test
    void staffCannotManageSomeoneMorePowerful() throws Exception {
        String managerProfile = createProfile(ownerA, unique("Big"), "access.staff.read", "access.staff.manage",
                "access.profile.manage");
        String managerId = JsonPath.read(createStaff(ownerA, managerProfile), "$.id");

        String limitedProfile = createProfile(ownerA, unique("Small"), "access.staff.read", "access.staff.manage");
        RequestPostProcessor limited = Tokens.staff(JsonPath.read(createStaff(ownerA, limitedProfile), "$.userId"),
                GYM_A, converter);

        mvc.perform(post("/api/v1/staff/{id}/disable", managerId).with(limited))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("error.access_escalation"));
    }

    // ---------- helpers ----------

    private String createProfile(RequestPostProcessor as, String name, String... capabilities) throws Exception {
        String caps = String.join("\",\"", capabilities);
        String body = mvc.perform(json(post("/api/v1/profiles"),
                        "{\"name\":\"%s\",\"capabilities\":[%s]}".formatted(name, capabilities.length == 0 ? "" : "\"" + caps + "\""))
                        .with(as))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String createStaff(RequestPostProcessor as, String profileId) throws Exception {
        return createStaff(as, profileId, unique("staff") + "@gym.tn");
    }

    private String createStaff(RequestPostProcessor as, String profileId, String email) throws Exception {
        return mvc.perform(json(post("/api/v1/staff"), staffJson(email, profileId)).with(as))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static String staffJson(String email, String... profileIds) {
        String ids = profileIds.length == 0 || profileIds[0] == null ? "" : "\"" + String.join("\",\"", profileIds) + "\"";
        return "{\"email\":\"%s\",\"firstName\":\"Sami\",\"lastName\":\"Trabelsi\",\"profileIds\":[%s]}"
                .formatted(email, ids);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
