package com.gymora.access.domain;

import com.gymora.security.capability.DefaultProfileGrants.DefaultProfile;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** All queries are limited to the current gym by Hibernate's tenant filter. */
public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    List<Profile> findAllByOrderByNameAsc();

    List<Profile> findAllByIdIn(Collection<UUID> ids);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsByDefaultKey(DefaultProfile defaultKey);

    /** [profileId, number of staff members holding it] */
    @Query("select p.id, count(s) from StaffMember s join s.profiles p group by p.id")
    List<Object[]> countStaffPerProfile();
}
