package com.brixo.slidehub.ui.repository;

import com.brixo.slidehub.ui.model.Presentation;
import com.brixo.slidehub.ui.model.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA de presentaciones.
 */
public interface PresentationRepository extends JpaRepository<Presentation, String> {

    List<Presentation> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Presentation> findByIdAndUserId(String id, String userId);

    @Query("select p.id from Presentation p where p.user.id = :userId")
    List<String> findIdsByUserId(@Param("userId") String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Presentation p where p.user.id = :userId")
    int deleteByUserId(@Param("userId") String userId);

    long countBySourceType(SourceType sourceType);

    List<Presentation> findAllByOrderByCreatedAtDesc();
}
