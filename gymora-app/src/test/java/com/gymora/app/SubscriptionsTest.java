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
import com.gymora.subscription.domain.DurationUnit;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@GymoraIntegrationTest
class SubscriptionsTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    GymoraAuthoritiesConverter converter;

    @Autowired
    DefaultProfileSeeder seeder;

    RequestPostProcessor ownerA;
    RequestPostProcessor ownerB;
    LocalDate today;

    @BeforeEach
    void setUp() {
        ownerA = Tokens.owner(GYM_A, converter);
        ownerB = Tokens.owner(GYM_B, converter);
        today = LocalDate.now(ZoneId.of("Africa/Tunis"));
    }

    // ---------- plans ----------

    @Test
    void planNamesAreUniquePerGymAndChangesNeverTouchSoldSubscriptions() throws Exception {
        String name = "Mensuel " + suffix();
        String planId = plan(ownerA, name, 1, "MONTH", 60_000, 7);

        mvc.perform(json(post("/api/v1/plans"), planJson(name.toUpperCase(), 1, "MONTH", 50_000, 0)).with(ownerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.plan_name_taken"));
        plan(ownerB, name, 1, "MONTH", 55_000, 0); // another gym may use the same name

        String memberId = member(ownerA);
        String subscriptionId = JsonPath.read(sell(ownerA, memberId, saleJson(planId, null, 0, 0)), "$.subscription.id");

        mvc.perform(json(put("/api/v1/plans/{id}", planId), planJson(name, 2, "MONTH", 70_000, 15)).with(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceMillimes").value(70_000))
                .andExpect(jsonPath("$.runningSubscriptions").value(1));
        mvc.perform(get("/api/v1/subscriptions/{id}", subscriptionId).with(ownerA))
                .andExpect(jsonPath("$.priceMillimes").value(60_000))
                .andExpect(jsonPath("$.maxFreezeDays").value(7))
                .andExpect(jsonPath("$.endDate").value(DurationUnit.MONTH.lastDay(today, 1).toString()));

        mvc.perform(post("/api/v1/plans/{id}/archive", planId).with(ownerA))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/v1/plans").with(ownerA)).andExpect(jsonPath("$[*].id", not(hasItem(planId))));
        mvc.perform(get("/api/v1/plans").param("status", "ARCHIVED").with(ownerA)).andExpect(jsonPath("$[*].id", hasItem(planId)));
        mvc.perform(json(post("/api/v1/members/{id}/subscriptions", member(ownerA)), saleJson(planId, null, 0, 0)).with(ownerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.plan_archived"));
    }

    // ---------- selling ----------

    @Test
    void renewalsStartTheDayAfterAndPeriodsNeverOverlap() throws Exception {
        String planId = plan(ownerA, "Trimestriel " + suffix(), 3, "MONTH", 160_000, 15);
        String memberId = member(ownerA);
        LocalDate end = DurationUnit.MONTH.lastDay(today, 3);

        mvc.perform(get("/api/v1/members/{id}/subscriptions/next-start", memberId).with(ownerA))
                .andExpect(jsonPath("$.startDate").value(today.toString()));
        String first = sell(ownerA, memberId, saleJson(planId, null, 0, 0));
        assertThat((String) JsonPath.read(first, "$.subscription.startDate")).isEqualTo(today.toString());
        assertThat((String) JsonPath.read(first, "$.subscription.endDate")).isEqualTo(end.toString());
        assertThat((String) JsonPath.read(first, "$.subscription.status")).isEqualTo("ACTIVE");

        String renewal = sell(ownerA, memberId, saleJson(planId, null, 0, 0));
        assertThat((String) JsonPath.read(renewal, "$.subscription.startDate")).isEqualTo(end.plusDays(1).toString());
        assertThat((String) JsonPath.read(renewal, "$.subscription.status")).isEqualTo("UPCOMING");

        mvc.perform(json(post("/api/v1/members/{id}/subscriptions", memberId), saleJson(planId, today.plusDays(10), 0, 0)).with(ownerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.subscription_overlap"));

        mvc.perform(get("/api/v1/members/{id}/subscriptions", memberId).with(ownerA))
                .andExpect(jsonPath("$[*].status", contains("UPCOMING", "ACTIVE")));
    }

    @Test
    void archivedMembersCannotBuyAndDiscountsAreCapped() throws Exception {
        String planId = plan(ownerA, "Étudiant " + suffix(), 1, "MONTH", 45_000, 0);
        String memberId = member(ownerA);

        mvc.perform(json(post("/api/v1/members/{id}/subscriptions", memberId), saleJson(planId, null, 50_000, 0)).with(ownerA))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("error.discount_too_high"));

        mvc.perform(post("/api/v1/members/{id}/archive", memberId).with(ownerA)).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/members/{id}/subscriptions", memberId), saleJson(planId, null, 0, 0)).with(ownerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.subscription_member_archived"));
    }

    // ---------- payments ----------

    @Test
    void cashPaymentsCanBeSplitAndVoidedButNeverExceedWhatIsDue() throws Exception {
        String planId = plan(ownerA, "Trimestriel " + suffix(), 3, "MONTH", 160_000, 15);
        String memberId = member(ownerA);

        String sale = sell(ownerA, memberId, saleJson(planId, null, 10_000, 100_000));
        String subscriptionId = JsonPath.read(sale, "$.subscription.id");
        String firstPaymentId = JsonPath.read(sale, "$.payment.id");
        assertThat((Integer) JsonPath.read(sale, "$.subscription.amountDueMillimes")).isEqualTo(150_000);
        assertThat((Integer) JsonPath.read(sale, "$.subscription.remainingMillimes")).isEqualTo(50_000);
        assertThat((String) JsonPath.read(sale, "$.payment.receiptNumber")).matches("R-\\d{6}");
        assertThat((String) JsonPath.read(sale, "$.payment.method")).isEqualTo("CASH");

        mvc.perform(json(post("/api/v1/subscriptions/{id}/payments", subscriptionId), "{\"amountMillimes\":60000}").with(ownerA))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("error.payment_too_high"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("50,000 DT")));
        String second = mvc.perform(json(post("/api/v1/subscriptions/{id}/payments", subscriptionId),
                        "{\"amountMillimes\":50000,\"note\":\"Solde\"}").with(ownerA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receivedByName").value("owner-" + GYM_A))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(json(post("/api/v1/subscriptions/{id}/payments", subscriptionId), "{\"amountMillimes\":1000}").with(ownerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.payment_nothing_due"));

        mvc.perform(json(post("/api/v1/payments/{id}/void", firstPaymentId), "{\"reason\":\"Erreur de saisie\"}").with(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voidReason").value("Erreur de saisie"));
        mvc.perform(json(post("/api/v1/payments/{id}/void", firstPaymentId), "{\"reason\":\"Encore\"}").with(ownerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.payment_already_voided"));
        mvc.perform(get("/api/v1/subscriptions/{id}", subscriptionId).with(ownerA))
                .andExpect(jsonPath("$.paidMillimes").value(50_000))
                .andExpect(jsonPath("$.remainingMillimes").value(100_000));

        // The day's cash lists the valid payment, not the voided one.
        String secondReceipt = JsonPath.read(second, "$.receiptNumber");
        String firstReceipt = JsonPath.read(sale, "$.payment.receiptNumber");
        mvc.perform(get("/api/v1/payments/day").with(ownerA))
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.payments[*].payment.receiptNumber", hasItem(secondReceipt)))
                .andExpect(jsonPath("$.payments[*].payment.receiptNumber", not(hasItem(firstReceipt))));
        mvc.perform(get("/api/v1/members/{id}/payments", memberId).with(ownerA))
                .andExpect(jsonPath("$[*].receiptNumber", contains(secondReceipt, firstReceipt)));
    }

    // ---------- freezes ----------

    @Test
    void freezingMovesTheEndAndQueuedRenewalsAndUnfreezingGivesTheDaysBack() throws Exception {
        String planId = plan(ownerA, "Mensuel gel " + suffix(), 1, "MONTH", 60_000, 10);
        String memberId = member(ownerA);
        String current = JsonPath.read(sell(ownerA, memberId, saleJson(planId, null, 0, 0)), "$.subscription.id");
        String renewal = JsonPath.read(sell(ownerA, memberId, saleJson(planId, null, 0, 0)), "$.subscription.id");
        LocalDate end = DurationUnit.MONTH.lastDay(today, 1);

        mvc.perform(json(post("/api/v1/subscriptions/{id}/freeze", current), freezeJson(today, today.plusDays(4))).with(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.frozenDays").value(5))
                .andExpect(jsonPath("$.freezeDaysLeft").value(5))
                .andExpect(jsonPath("$.endDate").value(end.plusDays(5).toString()));
        mvc.perform(get("/api/v1/subscriptions/{id}", renewal).with(ownerA))
                .andExpect(jsonPath("$.startDate").value(end.plusDays(6).toString()));
        mvc.perform(get("/api/v1/subscriptions").param("view", "FROZEN").with(ownerA))
                .andExpect(jsonPath("$.items[*].subscription.id", hasItem(current)));

        mvc.perform(json(post("/api/v1/subscriptions/{id}/freeze", current), freezeJson(today.plusDays(6), today.plusDays(6))).with(ownerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.freeze_already"));

        // Ended on its first day: every day comes back.
        mvc.perform(post("/api/v1/subscriptions/{id}/unfreeze", current).with(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.frozenDays").value(0))
                .andExpect(jsonPath("$.endDate").value(end.toString()));
        mvc.perform(get("/api/v1/subscriptions/{id}", renewal).with(ownerA))
                .andExpect(jsonPath("$.startDate").value(end.plusDays(1).toString()));

        mvc.perform(json(post("/api/v1/subscriptions/{id}/freeze", current), freezeJson(today.plusDays(1), today.plusDays(11))).with(ownerA))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("error.freeze_too_long"));
        mvc.perform(json(post("/api/v1/subscriptions/{id}/freeze", current), freezeJson(today.minusDays(1), today)).with(ownerA))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("error.freeze_in_past"));
        mvc.perform(json(post("/api/v1/subscriptions/{id}/freeze", current), freezeJson(today.plusDays(3), today.plusDays(2))).with(ownerA))
                .andExpect(jsonPath("$.code").value("error.freeze_dates"));
        mvc.perform(post("/api/v1/subscriptions/{id}/unfreeze", current).with(ownerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.freeze_none"));

        String noFreezePlan = plan(ownerA, "Sans gel " + suffix(), 1, "MONTH", 45_000, 0);
        String other = JsonPath.read(sell(ownerA, member(ownerA), saleJson(noFreezePlan, null, 0, 0)), "$.subscription.id");
        mvc.perform(json(post("/api/v1/subscriptions/{id}/freeze", other), freezeJson(today, today)).with(ownerA))
                .andExpect(jsonPath("$.code").value("error.freeze_not_allowed"));
    }

    // ---------- cancelling ----------

    @Test
    void cancellingStopsTheSubscriptionAndFreesTheDates() throws Exception {
        String planId = plan(ownerA, "Mensuel annul " + suffix(), 1, "MONTH", 60_000, 0);
        String memberId = member(ownerA);
        String id = JsonPath.read(sell(ownerA, memberId, saleJson(planId, null, 0, 20_000)), "$.subscription.id");

        mvc.perform(json(post("/api/v1/subscriptions/{id}/cancel", id), "{\"reason\":\"Déménagement à Sfax\"}").with(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Déménagement à Sfax"))
                .andExpect(jsonPath("$.cancelledByName").value("owner-" + GYM_A))
                .andExpect(jsonPath("$.paidMillimes").value(20_000));
        mvc.perform(json(post("/api/v1/subscriptions/{id}/payments", id), "{\"amountMillimes\":1000}").with(ownerA))
                .andExpect(jsonPath("$.code").value("error.subscription_cancelled"));
        mvc.perform(json(post("/api/v1/subscriptions/{id}/cancel", id), "{\"reason\":\"x\"}").with(ownerA))
                .andExpect(status().isConflict());

        String again = sell(ownerA, memberId, saleJson(planId, null, 0, 0));
        assertThat((String) JsonPath.read(again, "$.subscription.startDate")).isEqualTo(today.toString());
    }

    // ---------- follow-up lists ----------

    @Test
    void followUpListsFindExpiringExpiredAndUnpaidSubscriptions() throws Exception {
        String planId = plan(ownerA, "30 jours " + suffix(), 30, "DAY", 50_000, 0);
        String lastName = "Suivi" + suffix();
        String expiring = member(ownerA, lastName);
        String expired = member(ownerA, lastName);
        String expiringSub = JsonPath.read(sell(ownerA, expiring, saleJson(planId, today.minusDays(25), 0, 50_000)), "$.subscription.id");
        String expiredSub = JsonPath.read(sell(ownerA, expired, saleJson(planId, today.minusDays(40), 0, 0)), "$.subscription.id");

        mvc.perform(get("/api/v1/subscriptions").param("view", "EXPIRING").param("q", lastName).with(ownerA))
                .andExpect(jsonPath("$.items[*].subscription.id", contains(expiringSub)))
                .andExpect(jsonPath("$.items[0].subscription.daysLeft").value(5))
                .andExpect(jsonPath("$.items[0].member.lastName").value(lastName))
                .andExpect(jsonPath("$.items[0].renewed").value(false));
        mvc.perform(get("/api/v1/subscriptions").param("view", "EXPIRED").param("q", lastName).with(ownerA))
                .andExpect(jsonPath("$.items[*].subscription.id", contains(expiredSub)))
                .andExpect(jsonPath("$.items[0].subscription.status").value("EXPIRED"));
        mvc.perform(get("/api/v1/subscriptions").param("view", "UNPAID").param("q", lastName).with(ownerA))
                .andExpect(jsonPath("$.items[*].subscription.id", contains(expiredSub)));

        sell(ownerA, expiring, saleJson(planId, null, 0, 0)); // renewed
        mvc.perform(get("/api/v1/subscriptions").param("view", "EXPIRING").param("q", lastName).with(ownerA))
                .andExpect(jsonPath("$.items[0].renewed").value(true));

        String counts = mvc.perform(get("/api/v1/subscriptions/counts").with(ownerA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(counts, "$.expiringSoon")).isGreaterThanOrEqualTo(1);
        assertThat(((Number) JsonPath.read(counts, "$.unpaidMillimes")).longValue()).isGreaterThanOrEqualTo(50_000);
    }

    // ---------- tenancy and capabilities ----------

    @Test
    void anotherGymCannotSeeOrTouchSubscriptionsPlansOrPayments() throws Exception {
        String planId = plan(ownerA, "Privé " + suffix(), 1, "MONTH", 60_000, 10);
        String memberId = member(ownerA);
        String sale = sell(ownerA, memberId, saleJson(planId, null, 0, 10_000));
        String id = JsonPath.read(sale, "$.subscription.id");
        String paymentId = JsonPath.read(sale, "$.payment.id");

        mvc.perform(get("/api/v1/subscriptions/{id}", id).with(ownerB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/plans/{id}", planId).with(ownerB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/members/{id}/subscriptions", memberId).with(ownerB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/members/{id}/payments", memberId).with(ownerB)).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/v1/subscriptions/{id}/payments", id), "{\"amountMillimes\":1000}").with(ownerB))
                .andExpect(status().isNotFound());
        mvc.perform(json(post("/api/v1/subscriptions/{id}/freeze", id), freezeJson(today, today)).with(ownerB))
                .andExpect(status().isNotFound());
        mvc.perform(json(post("/api/v1/payments/{id}/void", paymentId), "{\"reason\":\"x\"}").with(ownerB))
                .andExpect(status().isNotFound());
        // Gym A's plan can't be sold by gym B, even to its own member.
        mvc.perform(json(post("/api/v1/members/{id}/subscriptions", member(ownerB)), saleJson(planId, null, 0, 0)).with(ownerB))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/subscriptions").param("view", "ACTIVE").with(ownerB))
                .andExpect(jsonPath("$.items[*].subscription.id", not(hasItem(id))));
    }

    @Test
    void trainersSeeDatesButNoMoneyAndReceptionistsCannotVoidOrCancel() throws Exception {
        String planId = plan(ownerA, "Accès " + suffix(), 1, "MONTH", 60_000, 10);
        String memberId = member(ownerA);
        String sale = sell(ownerA, memberId, saleJson(planId, null, 0, 10_000));
        String id = JsonPath.read(sale, "$.subscription.id");
        String paymentId = JsonPath.read(sale, "$.payment.id");

        RequestPostProcessor trainer = staffWith("subscription.subscription.read");
        mvc.perform(get("/api/v1/members/{id}/subscriptions", memberId).with(trainer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].priceMillimes").doesNotExist())
                .andExpect(jsonPath("$[0].remainingMillimes").doesNotExist());
        mvc.perform(get("/api/v1/subscriptions/counts").with(trainer)).andExpect(jsonPath("$.unpaidMillimes").doesNotExist());
        mvc.perform(get("/api/v1/subscriptions").param("view", "UNPAID").with(trainer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/members/{id}/payments", memberId).with(trainer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/plans").with(trainer)).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/members/{id}/subscriptions", memberId), saleJson(planId, null, 0, 0)).with(trainer))
                .andExpect(status().isForbidden());

        RequestPostProcessor receptionist = staffWith(seeder.grantsFor(DefaultProfile.RECEPTIONIST).toArray(String[]::new));
        mvc.perform(json(post("/api/v1/subscriptions/{id}/payments", id), "{\"amountMillimes\":5000}").with(receptionist))
                .andExpect(status().isCreated());
        mvc.perform(json(post("/api/v1/subscriptions/{id}/freeze", id), freezeJson(today.plusDays(1), today.plusDays(2))).with(receptionist))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/payments/{id}/void", paymentId), "{\"reason\":\"x\"}").with(receptionist))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/subscriptions/{id}/cancel", id), "{\"reason\":\"x\"}").with(receptionist))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/plans"), planJson("Nope " + suffix(), 1, "MONTH", 1, 0)).with(receptionist))
                .andExpect(status().isForbidden());
    }

    @Test
    void startingProfilesGetSubscriptionCapabilitiesAndTheCatalogNamesTheModule() throws Exception {
        assertThat(seeder.grantsFor(DefaultProfile.MANAGER)).contains("subscription.payment.void", "subscription.plan.manage");
        assertThat(seeder.grantsFor(DefaultProfile.RECEPTIONIST))
                .contains("subscription.subscription.sell", "subscription.payment.record", "subscription.subscription.freeze")
                .doesNotContain("subscription.payment.void", "subscription.subscription.cancel", "subscription.plan.manage");
        assertThat(seeder.grantsFor(DefaultProfile.TRAINER))
                .contains("subscription.subscription.read")
                .doesNotContain("subscription.payment.read");
        mvc.perform(get("/api/v1/capabilities").with(ownerA))
                .andExpect(jsonPath("$[?(@.module=='subscription')].label", contains("Abonnements")));
    }

    @Test
    void validationErrorsListTheFields() throws Exception {
        mvc.perform(json(post("/api/v1/plans"), "{\"name\":\"\",\"durationValue\":0,\"priceMillimes\":-1}").with(ownerA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("durationValue")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("durationUnit")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("priceMillimes")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("maxFreezeDays")));
        String memberId = member(ownerA);
        mvc.perform(json(post("/api/v1/members/{id}/subscriptions", memberId), "{\"paymentMillimes\":-5}").with(ownerA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("planId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("paymentMillimes")));
    }

    // ---------- helpers ----------

    private String plan(RequestPostProcessor as, String name, int length, String unit, long price, int freezeDays) throws Exception {
        String body = mvc.perform(json(post("/api/v1/plans"), planJson(name, length, unit, price, freezeDays)).with(as))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String planJson(String name, int length, String unit, long price, int freezeDays) {
        return """
                {"name":"%s","durationValue":%d,"durationUnit":"%s","priceMillimes":%d,"maxFreezeDays":%d}"""
                .formatted(name, length, unit, price, freezeDays);
    }

    private String member(RequestPostProcessor as) throws Exception {
        return member(as, "Membre" + suffix());
    }

    private String member(RequestPostProcessor as, String lastName) throws Exception {
        String phone = "9" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999);
        String body = mvc.perform(json(post("/api/v1/members"),
                        "{\"firstName\":\"Test\",\"lastName\":\"%s\",\"phone\":\"%s\"}".formatted(lastName, phone)).with(as))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String sell(RequestPostProcessor as, String memberId, String saleJson) throws Exception {
        ResultActions result = mvc.perform(json(post("/api/v1/members/{id}/subscriptions", memberId), saleJson).with(as));
        return result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscription.id").value(matchesPattern("[0-9a-f-]{36}")))
                .andReturn().getResponse().getContentAsString();
    }

    private static String saleJson(String planId, LocalDate start, long discount, long payNow) {
        return """
                {"planId":"%s","startDate":%s,"discountMillimes":%d,"paymentMillimes":%d}"""
                .formatted(planId, start == null ? "null" : "\"" + start + "\"", discount, payNow);
    }

    private static String freezeJson(LocalDate from, LocalDate to) {
        return "{\"startDate\":\"%s\",\"endDate\":\"%s\",\"reason\":\"Voyage\"}".formatted(from, to);
    }

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

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
