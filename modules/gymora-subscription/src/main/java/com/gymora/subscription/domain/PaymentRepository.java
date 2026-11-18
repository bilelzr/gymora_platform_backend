package com.gymora.subscription.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** All queries are limited to the current gym by Hibernate's tenant filter. */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findBySubscriptionIdOrderByPaidAtDesc(UUID subscriptionId);

    List<Payment> findByMemberIdOrderByPaidAtDesc(UUID memberId);

    List<Payment> findByPaidOnOrderByPaidAtDesc(LocalDate day);
}
