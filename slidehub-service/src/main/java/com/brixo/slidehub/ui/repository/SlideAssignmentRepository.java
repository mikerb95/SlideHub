package com.codebymike.slidehub.ui.repository;

import com.codebymike.slidehub.ui.model.SlideAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SlideAssignmentRepository extends JpaRepository<SlideAssignment, String> {

    List<SlideAssignment> findByPresentationIdOrderBySlideNumberAsc(String presentationId);

    Optional<SlideAssignment> findByPresentationIdAndSlideNumber(String presentationId, int slideNumber);

    void deleteByParticipantId(String participantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SlideAssignment sa where sa.presentation.id in :presentationIds")
    int deleteByPresentationIdIn(@Param("presentationIds") List<String> presentationIds);
}
