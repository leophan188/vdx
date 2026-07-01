package com.bpm.infrastructure;

import com.bpm.domain.social.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, String> {

    Optional<PostLike> findByPostIdAndUserId(String postId, String userId);

    boolean existsByPostIdAndUserId(String postId, String userId);

    long countByPostId(String postId);

    /** Like của user trên một tập bài (để đánh dấu trạng thái "đã like" cho feed). */
    List<PostLike> findByUserIdAndPostIdIn(String userId, List<String> postIds);

    /** Đếm hàng loạt cho feed. */
    List<PostLike> findByPostIdIn(List<String> postIds);
}
