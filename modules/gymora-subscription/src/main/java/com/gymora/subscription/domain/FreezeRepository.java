package com.gymora.subscription.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** All queries are limited to the current gym by Hibernate's tenant filter. */
public interface FreezeRepository extends JpaRepository<Freeze, UUID> {

    List<Freeze> findBySubscriptionIdInOrderByStartDateAsc(Collection<UUID> subscriptionIds);

    /** The freeze running now or planned, if any (a subscription has at most one at a time). */
    Optional<Freeze> findFirstBySubscriptionIdAndEndDateGreaterThanEqualOrderByStartDateAsc(UUID subscriptionId,
                                                                                           LocalDate day);
}
