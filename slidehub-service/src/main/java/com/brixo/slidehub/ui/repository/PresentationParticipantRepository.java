package com.codebymike.slidehub.ui.repository;

import com.codebymike.slidehub.ui.model.PresentationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PresentationParticipantRepository extends JpaRepository<PresentationParticipant, String> {

    List<PresentationParticipant> findByPresentationIdOrderByDisplayNameAsc(String presentationId);

    Optional<PresentationParticipant> findByIdAndPresentationId(String id, String presentationId);

    boolean existsByPresentationIdAndDisplayNameIgnoreCase(String presentationId, String displayName);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PresentationParticipant p where p.presentation.id in :presentationIds")
    int deleteByPresentationIdIn(@Param("presentationIds") List<String> presentationIds);
}
