package com.gymora.access.application;

import com.gymora.access.domain.Profile;
import com.gymora.access.domain.ProfileRepository;
import com.gymora.common.tenant.TenantContext;
import com.gymora.security.capability.DefaultProfileGrants;
import com.gymora.security.capability.DefaultProfileGrants.DefaultProfile;
import com.gymora.tenant.GymCreatedEvent;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gives every new gym its starting profiles (Manager, Receptionist, Trainer), built from what each
 * module contributes through {@link DefaultProfileGrants}. Profile names are in French, the
 * default language for gyms. Safe to run again: existing starting profiles are left untouched.
 */
@Component
public class DefaultProfileSeeder {

    private static final Logger log = LoggerFactory.getLogger(DefaultProfileSeeder.class);

    private final ProfileRepository profiles;
    private final List<DefaultProfileGrants> contributions;
    private final MessageSource messages;

    public DefaultProfileSeeder(ProfileRepository profiles, List<DefaultProfileGrants> contributions,
                                MessageSource messages) {
        this.profiles = profiles;
        this.contributions = contributions;
        this.messages = messages;
    }

    /** Seeds the gym that is current in {@link TenantContext}, in its own transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedCurrentGym() {
        TenantContext.require();
        for (DefaultProfile template : DefaultProfile.values()) {
            if (profiles.existsByDefaultKey(template)) {
                continue;
            }
            String key = "access.profile.default." + template.name().toLowerCase(Locale.ROOT);
            profiles.save(new Profile(
                    messages.getMessage(key, null, template.name(), Locale.FRENCH),
                    messages.getMessage(key + ".description", null, null, Locale.FRENCH),
                    grantsFor(template),
                    template));
        }
    }

    /** What a new gym's starting profile gets, merged from every module's contribution. */
    public Set<String> grantsFor(DefaultProfile template) {
        Set<String> result = new TreeSet<>();
        contributions.forEach(contribution -> result.addAll(contribution.grants().getOrDefault(template, Set.of())));
        return result;
    }

    @Component
    static class OnGymCreated {

        private final DefaultProfileSeeder seeder;

        OnGymCreated(DefaultProfileSeeder seeder) {
            this.seeder = seeder;
        }

        @TransactionalEventListener
        void seed(GymCreatedEvent event) {
            try {
                TenantContext.runAs(event.gymId(), seeder::seedCurrentGym);
            } catch (RuntimeException e) {
                // The gym exists; its owner can create profiles by hand, or the seeding can be re-run.
                log.error("Could not seed default profiles for gym {}", event.gymId(), e);
            }
        }
    }
}
