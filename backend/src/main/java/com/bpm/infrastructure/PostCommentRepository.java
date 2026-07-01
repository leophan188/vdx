package com.bpm.infrastructure;

import com.bpm.domain.social.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostComment, String> {

    /** Bình luận hiển thị (chưa ẩn) theo bài, cũ → mới. */
    List<PostComment> findByPostIdAndHiddenFalseOrderByCreatedAtAsc(String postId);

    long countByPostIdAndHiddenFalse(String postId);

    /** Đếm hàng loạt cho feed (tránh N+1). */
    List<PostComment> findByPostIdInAndHiddenFalse(List<String> postIds);

    /** Tác giả các bình luận chưa ẩn của một bài (để thông báo bình luận mới — FR-B07). */
    List<PostComment> findByPostIdAndHiddenFalse(String postId);
}
