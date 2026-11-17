package com.gymora.app;

import static com.gymora.app.support.Tokens.GYM_A;
import static com.gymora.app.support.Tokens.GYM_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymora.access.application.DefaultProfileSeeder;
import com.gymora.app.support.GymoraIntegrationTest;
import com.gymora.app.support.Tokens;
import com.gymora.security.capability.DefaultProfileGrants.DefaultProfile;
import com.gymora.security.jwt.GymoraAuthoritiesConverter;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@GymoraIntegrationTest
class MembersTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    GymoraAuthoritiesConverter converter;

    @Autowired
    DefaultProfileSeeder seeder;

    RequestPostProcessor ownerA;
    RequestPostProcessor ownerB;

    @BeforeEach
    void setUp() {
        ownerA = Tokens.owner(GYM_A, converter);
        ownerB = Tokens.owner(GYM_B, converter);
    }

    @Test
    void registeringGivesTheGymsNextMemberNumber() throws Exception {
        String first = register(ownerA, "Sami", "Trabelsi", randomPhone());
        String second = register(ownerA, "Ines", "Jaziri", randomPhone());

        String firstNumber = JsonPath.read(first, "$.memberNumber");
        String secondNumber = JsonPath.read(second, "$.memberNumber");
        assertThat(firstNumber).matches("M-\\d{6}");
        assertThat(Integer.parseInt(secondNumber.substring(2))).isEqualTo(Integer.parseInt(firstNumber.substring(2)) + 1);

        // Each gym counts on its own.
        String inB = register(ownerB, "Karim", "Ben Ali", randomPhone());
        assertThat((String) JsonPath.read(inB, "$.memberNumber")).matches("M-\\d{6}");
        assertThat((String) JsonPath.read(first, "$.status")).isEqualTo("ACTIVE");
    }

    @Test
    void phoneNumbersAreNormalizedAndUniquePerGym() throws Exception {
        String digits = randomPhone();
        String spaced = digits.substring(0, 2) + " " + digits.substring(2, 5) + " " + digits.substring(5);
        String body = register(ownerA, "Leila", "Gharbi", spaced);
        assertThat((String) JsonPath.read(body, "$.phone")).isEqualTo(digits);

        mvc.perform(json(post("/api/v1/members"), memberJson("Autre", "Personne", digits)).with(ownerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.member_phone_taken"));

        // Another gym can have a member with the same phone.
        register(ownerB, "Leila", "Gharbi", digits);

        mvc.perform(json(post("/api/v1/members"), memberJson("Bad", "Phone", "12")).with(ownerA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("error.member_phone_invalid"));
    }

    @Test
    void membersCanBeFoundByNameNumberOrPhone() throws Exception {
        String phone = randomPhone();
        String lastName = "Zorgati" + UUID.randomUUID().toString().substring(0, 6);
        String body = register(ownerA, "Youssef", lastName, phone);
        String number = JsonPath.read(body, "$.memberNumber");
        String id = JsonPath.read(body, "$.id");

        for (String query : List.of("youssef " + lastName.toLowerCase(), lastName.toUpperCase(), number, phone.substring(2, 7))) {
            mvc.perform(get("/api/v1/members").param("q", query).with(ownerA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", hasItem(id)));
        }
        mvc.perform(get("/api/v1/members").param("q", lastName).with(ownerB))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(id))));
    }

    @Test
    void archivedMembersAreReadOnlyUntilReactivated() throws Exception {
        String phone = randomPhone();
        String id = JsonPath.read(register(ownerA, "Hela", "Mabrouk", phone), "$.id");

        mvc.perform(post("/api/v1/members/{id}/archive", id).with(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        mvc.perform(json(put("/api/v1/members/{id}", id), memberJson("Hela", "Changed", phone)).with(ownerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.member_archived"));

        mvc.perform(get("/api/v1/members").param("status", "ARCHIVED").with(ownerA))
                .andExpect(jsonPath("$.items[*].id", hasItem(id)));

        mvc.perform(post("/api/v1/members/{id}/reactivate", id).with(ownerA))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(json(put("/api/v1/members/{id}", id), memberJson("Hela", "Changed", phone)).with(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Changed"));
    }

    @Test
    void notesKeepTheirAuthorAndComeNewestFirst() throws Exception {
        String id = JsonPath.read(register(ownerA, "Rania", "Saidi", randomPhone()), "$.id");

        mvc.perform(json(post("/api/v1/members/{id}/notes", id), "{\"body\":\"Genou fragile : éviter les squats lourds.\"}").with(ownerA))
                .andExpect(status().isCreated());
        mvc.perform(json(post("/api/v1/members/{id}/notes", id), "{\"body\":\"Préfère les cours du soir.\"}").with(ownerA))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/members/{id}/notes", id).with(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].body", contains("Préfère les cours du soir.", "Genou fragile : éviter les squats lourds.")))
                .andExpect(jsonPath("$[0].authorName").value("owner-" + GYM_A));

        mvc.perform(json(post("/api/v1/members/{id}/notes", id), "{\"body\":\"  \"}").with(ownerA))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherGymCannotSeeOrChangeAMember() throws Exception {
        String phone = randomPhone();
        String id = JsonPath.read(register(ownerA, "Amel", "Chebbi", phone), "$.id");

        mvc.perform(get("/api/v1/members/{id}", id).with(ownerB)).andExpect(status().isNotFound());
        mvc.perform(json(put("/api/v1/members/{id}", id), memberJson("X", "Y", phone)).with(ownerB)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/members/{id}/archive", id).with(ownerB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/members/{id}/notes", id).with(ownerB)).andExpect(status().isNotFound());
    }

    @Test
    void staffNeedTheMatchingCapability() throws Exception {
        RequestPostProcessor trainer = staffWith("membership.member.read", "membership.note.read");
        String id = JsonPath.read(register(ownerA, "Walid", "Hamdi", randomPhone()), "$.id");

        mvc.perform(get("/api/v1/members/{id}", id).with(trainer)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/members/{id}/notes", id).with(trainer)).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/members"), memberJson("A", "B", randomPhone())).with(trainer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/members/{id}/archive", id).with(trainer)).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/members/{id}/notes", id), "{\"body\":\"x\"}").with(trainer)).andExpect(status().isForbidden());
    }

    @Test
    void startingProfilesGetMembershipCapabilities() {
        assertThat(seeder.grantsFor(DefaultProfile.RECEPTIONIST))
                .contains("membership.member.read", "membership.member.create", "membership.member.update",
                        "membership.note.read", "membership.note.create")
                .doesNotContain("membership.member.archive");
        assertThat(seeder.grantsFor(DefaultProfile.TRAINER))
                .containsExactlyInAnyOrder("membership.member.read", "membership.note.read");
        assertThat(seeder.grantsFor(DefaultProfile.MANAGER)).contains("membership.member.archive");
    }

    @Test
    void membershipCapabilitiesAppearInTheCatalog() throws Exception {
        mvc.perform(get("/api/v1/capabilities").with(ownerA))
                .andExpect(jsonPath("$[?(@.module=='membership')].label", contains("Adhérents")))
                .andExpect(jsonPath("$[?(@.module=='membership')].capabilities[*].key", hasItem("membership.note.create")));
    }

    @Test
    void validationErrorsListTheFields() throws Exception {
        mvc.perform(json(post("/api/v1/members"), "{\"firstName\":\"\",\"lastName\":\"X\",\"phone\":\"\",\"email\":\"nope\"}").with(ownerA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("firstName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("phone")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")));
        mvc.perform(json(post("/api/v1/members"), "{\"firstName\":\"A\",\"lastName\":\"B\",\"phone\":\"" + randomPhone()
                        + "\",\"birthDate\":\"2999-01-01\"}").with(ownerA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("birthDate")));
    }

    // ---------- helpers ----------

    /** A staff member of gym A holding exactly these capabilities, through a fresh profile. */
    private RequestPostProcessor staffWith(String... capabilities) throws Exception {
        String caps = "\"" + String.join("\",\"", capabilities) + "\"";
        String profile = mvc.perform(json(post("/api/v1/profiles"),
                        "{\"name\":\"Test %s\",\"capabilities\":[%s]}".formatted(UUID.randomUUID(), caps)).with(ownerA))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String staff = mvc.perform(json(post("/api/v1/staff"),
                        "{\"email\":\"%s@gym.tn\",\"firstName\":\"T\",\"lastName\":\"R\",\"profileIds\":[\"%s\"]}"
                                .formatted(UUID.randomUUID(), JsonPath.read(profile, "$.id"))).with(ownerA))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Tokens.staff(JsonPath.read(staff, "$.userId"), GYM_A, converter);
    }

    private String register(RequestPostProcessor as, String first, String last, String phone) throws Exception {
        return mvc.perform(json(post("/api/v1/members"), memberJson(first, last, phone)).with(as))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberNumber").value(matchesPattern("M-\\d{6}")))
                .andReturn().getResponse().getContentAsString();
    }

    private static String memberJson(String first, String last, String phone) {
        return """
                {"firstName":"%s","lastName":"%s","phone":"%s","email":"%s@exemple.tn","gender":"FEMALE",
                 "birthDate":"1994-03-12","address":"Rue de Marseille, Tunis",
                 "emergencyContactName":"Mohamed","emergencyContactPhone":"+216 71 000 000"}"""
                .formatted(first, last, phone, first.toLowerCase());
    }

    /** A random 8-digit Tunisian-style mobile number. */
    private static String randomPhone() {
        return "9" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
