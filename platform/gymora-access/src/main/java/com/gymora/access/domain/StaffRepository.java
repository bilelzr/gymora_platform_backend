package com.gymora.access.domain;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** All queries are limited to the current gym by Hibernate's tenant filter. */
public interface StaffRepository extends JpaRepository<StaffMember, UUID>, JpaSpecificationExecutor<StaffMember> {

    Optional<StaffMember> findByUserId(String userId);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select count(s) > 0 from StaffMember s join s.profiles p where p.id = :profileId")
    boolean isProfileAssigned(UUID profileId);

    /** Capabilities of an active staff member, through all their profiles. */
    @Query("""
            select distinct c from StaffMember s join s.profiles p join p.capabilities c
            where s.userId = :userId and s.status = com.gymora.access.domain.StaffStatus.ACTIVE""")
    Set<String> activeCapabilitiesOf(String userId);
}
