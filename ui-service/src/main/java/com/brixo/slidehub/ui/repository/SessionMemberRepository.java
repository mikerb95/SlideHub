package com.brixo.slidehub.ui.repository;

import com.brixo.slidehub.ui.model.SessionMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessionMemberRepository extends JpaRepository<SessionMember, String> {

    Optional<SessionMember> findBySessionIdAndParticipantId(String sessionId, String participantId);

    Optional<SessionMember> findByParticipantTokenAndActiveTrue(String participantToken);

    List<SessionMember> findBySessionIdAndActiveTrue(String sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SessionMember sm where sm.session.presentation.id in :presentationIds")
    int deleteBySessionPresentationIdIn(@Param("presentationIds") List<String> presentationIds);
}
