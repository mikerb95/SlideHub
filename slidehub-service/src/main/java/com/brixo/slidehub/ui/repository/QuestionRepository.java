package com.codebymike.slidehub.ui.repository;

import com.codebymike.slidehub.ui.model.Question;
import com.codebymike.slidehub.ui.model.QuestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, String> {

    List<Question> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<Question> findBySessionIdAndStatusOrderByCreatedAtAsc(String sessionId, QuestionStatus status);

    @Modifying
    @Query("UPDATE Question q SET q.upvotes = q.upvotes + 1 WHERE q.id = :id")
    int incrementUpvotes(@Param("id") String id);
}
