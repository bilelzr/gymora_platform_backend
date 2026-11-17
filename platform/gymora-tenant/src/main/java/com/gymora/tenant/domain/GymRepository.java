package com.gymora.tenant.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface GymRepository extends JpaRepository<Gym, UUID>, JpaSpecificationExecutor<Gym> {

    @Query("select g.status from Gym g where g.id = :id")
    Optional<GymStatus> findStatusById(UUID id);
}
