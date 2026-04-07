package com.codebymike.slidehub.ui.repository;

import com.codebymike.slidehub.ui.model.PresentationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PresentationSessionRepository extends JpaRepository<PresentationSession, String> {

    boolean existsByActiveTrue();

    Optional<PresentationSession> findByPresentationIdAndActiveTrue(String presentationId);

    Optional<PresentationSession> findByPresentationIdAndJoinTokenAndActiveTrue(String presentationId,
            String joinToken);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PresentationSession s where s.presentation.id in :presentationIds")
    int deleteByPresentationIdIn(@Param("presentationIds") List<String> presentationIds);
}
