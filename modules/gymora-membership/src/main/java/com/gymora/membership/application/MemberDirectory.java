package com.gymora.membership.application;

import com.gymora.common.error.NotFoundException;
import com.gymora.membership.domain.Member;
import com.gymora.membership.domain.MemberRepository;
import com.gymora.membership.domain.MemberStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What other modules may know about members: who they are and whether they are archived.
 * Other modules use this instead of the membership tables, so the member model can change freely.
 */
@Component
public class MemberDirectory {

    /** Most members a cross-module name search returns. */
    private static final int SEARCH_LIMIT = 500;

    private final MemberRepository members;
    private final MemberService service;

    public MemberDirectory(MemberRepository members, MemberService service) {
        this.members = members;
        this.service = service;
    }

    @Transactional(readOnly = true)
    public MemberSummary require(UUID id) {
        return members.findById(id).map(MemberSummary::of).orElseThrow(() -> new NotFoundException("member", id));
    }

    @Transactional(readOnly = true)
    public Map<UUID, MemberSummary> findAll(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return members.findAllById(ids).stream().map(MemberSummary::of)
                .collect(Collectors.toMap(MemberSummary::id, Function.identity()));
    }

    /** Ids of members matching a search (name, number, phone or email), at most 500. */
    @Transactional(readOnly = true)
    public List<UUID> idsMatching(String query) {
        return service.search(query, null, PageRequest.of(0, SEARCH_LIMIT)).map(MemberViews.MemberView::id).getContent();
    }

    public record MemberSummary(UUID id, String memberNumber, String firstName, String lastName, String phone,
                                MemberStatus status) {

        static MemberSummary of(Member m) {
            return new MemberSummary(m.getId(), m.getMemberNumber(), m.getFirstName(), m.getLastName(), m.getPhone(),
                    m.getStatus());
        }

        public boolean archived() {
            return status == MemberStatus.ARCHIVED;
        }
    }
}
