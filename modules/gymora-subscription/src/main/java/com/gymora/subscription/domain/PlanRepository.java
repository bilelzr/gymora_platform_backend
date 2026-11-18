package com.gymora.subscription.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** All queries are limited to the current gym by Hibernate's tenant filter. */
public interface PlanRepository extends JpaRepository<Plan, UUID> {

    List<Plan> findByStatusOrderByNameAsc(PlanStatus status);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
