package com.gymora.membership.application;

import com.gymora.common.error.BusinessRuleException;
import com.gymora.common.error.NotFoundException;
import com.gymora.membership.application.MemberViews.MemberView;
import com.gymora.membership.application.MemberViews.NoteView;
import com.gymora.membership.domain.Member;
import com.gymora.membership.domain.Member.MemberDetails;
import com.gymora.membership.domain.MemberNote;
import com.gymora.membership.domain.MemberNoteRepository;
import com.gymora.membership.domain.MemberRepository;
import com.gymora.membership.domain.MemberStatus;
import com.gymora.security.user.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository members;
    private final MemberNoteRepository notes;
    private final MemberNumberGenerator numbers;
    private final Clock clock;

    public MemberService(MemberRepository members, MemberNoteRepository notes, MemberNumberGenerator numbers,
                         Clock clock) {
        this.members = members;
        this.notes = notes;
        this.numbers = numbers;
        this.clock = clock;
    }

    /** Matches name, member number, email or phone digits. */
    @Transactional(readOnly = true)
    public Page<MemberView> search(String query, MemberStatus status, Pageable pageable) {
        Specification<Member> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                List<Predicate> any = new ArrayList<>(List.of(
                        cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), like),
                        cb.like(cb.lower(cb.concat(cb.concat(root.get("lastName"), " "), root.get("firstName"))), like),
                        cb.like(cb.lower(root.get("memberNumber")), like),
                        cb.like(cb.lower(root.get("email")), like)));
                String digits = PhoneNumbers.searchDigits(query);
                if (!digits.isEmpty()) {
                    any.add(cb.like(root.get("phone"), "%" + digits + "%"));
                }
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return members.findAll(spec, pageable).map(MemberView::of);
    }

    @Transactional(readOnly = true)
    public MemberView get(UUID id) {
        return MemberView.of(find(id));
    }

    @Transactional
    public MemberView register(MemberDetails details) {
        if (members.existsByPhone(details.phone())) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.member_phone_taken", details.phone());
        }
        return MemberView.of(members.save(new Member(numbers.next(), details)));
    }

    @Transactional
    public MemberView update(UUID id, MemberDetails details) {
        Member member = find(id);
        if (member.isArchived()) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.member_archived");
        }
        if (members.existsByPhoneAndIdNot(details.phone(), id)) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.member_phone_taken", details.phone());
        }
        member.apply(details);
        members.flush();
        return MemberView.of(member);
    }

    @Transactional
    public MemberView archive(UUID id) {
        Member member = find(id);
        if (!member.isArchived()) {
            member.archive(clock.instant());
        }
        members.flush();
        return MemberView.of(member);
    }

    @Transactional
    public MemberView reactivate(UUID id) {
        Member member = find(id);
        member.reactivate();
        members.flush();
        return MemberView.of(member);
    }

    @Transactional(readOnly = true)
    public List<NoteView> notes(UUID memberId) {
        find(memberId);
        return notes.findByMemberIdOrderByCreatedAtDesc(memberId).stream().map(NoteView::of).toList();
    }

    @Transactional
    public NoteView addNote(UUID memberId, String body) {
        find(memberId);
        CurrentUser author = CurrentUser.get().orElseThrow();
        String name = author.fullName() != null && !author.fullName().isBlank() ? author.fullName() : author.username();
        return NoteView.of(notes.save(new MemberNote(memberId, body, name == null ? "?" : name)));
    }

    private Member find(UUID id) {
        return members.findById(id).orElseThrow(() -> new NotFoundException("member", id));
    }
}
