package com.gymora.subscription.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** All queries are limited to the current gym by Hibernate's tenant filter. */
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID>, JpaSpecificationExecutor<Subscription> {

    List<Subscription> findByMemberIdOrderByStartDateDesc(UUID memberId);

    /** The member's subscriptions (not cancelled) that share at least one day with the period. */
    @Query("""
            select s from Subscription s
            where s.memberId = :memberId and s.cancelledAt is null
              and s.startDate <= :lastDay and s.endDate >= :firstDay
            order by s.startDate""")
    List<Subscription> findOverlapping(@Param("memberId") UUID memberId, @Param("firstDay") LocalDate firstDay,
                                       @Param("lastDay") LocalDate lastDay);

    /** The member's subscription (not cancelled) that ends last. */
    Optional<Subscription> findFirstByMemberIdAndCancelledAtIsNullOrderByEndDateDesc(UUID memberId);

    /** Renewals queued after a day, in order. */
    List<Subscription> findByMemberIdAndCancelledAtIsNullAndStartDateAfterOrderByStartDateAsc(UUID memberId,
                                                                                             LocalDate day);

    boolean existsByMemberIdAndCancelledAtIsNullAndStartDateAfter(UUID memberId, LocalDate day);

    /** Rows of [planId, count] for subscriptions running on that day. */
    @Query("""
            select s.planId, count(s) from Subscription s
            where s.cancelledAt is null and s.startDate <= :day and s.endDate >= :day
            group by s.planId""")
    List<Object[]> runningCountsByPlan(@Param("day") LocalDate day);

    @Query("""
            select coalesce(sum(s.amountDueMillimes - s.paidMillimes), 0) from Subscription s
            where s.cancelledAt is null and s.paidMillimes < s.amountDueMillimes""")
    long unpaidTotalMillimes();
}
