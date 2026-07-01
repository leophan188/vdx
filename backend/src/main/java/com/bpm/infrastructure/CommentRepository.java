package com.bpm.infrastructure;

import com.bpm.domain.collab.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, String> {
    List<Comment> findByTargetTypeAndTargetIdOrderByCreatedAtAsc(String targetType, String targetId);
}
