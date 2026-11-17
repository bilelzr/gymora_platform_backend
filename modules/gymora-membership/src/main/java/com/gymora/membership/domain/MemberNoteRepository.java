package com.gymora.membership.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** All queries are limited to the current gym by Hibernate's tenant filter. */
public interface MemberNoteRepository extends JpaRepository<MemberNote, UUID> {

    List<MemberNote> findByMemberIdOrderByCreatedAtDesc(UUID memberId);
}
