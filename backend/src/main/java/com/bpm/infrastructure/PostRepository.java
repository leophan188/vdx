package com.bpm.infrastructure;

import com.bpm.domain.social.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, String> {

    /**
     * Feed: bài chưa ẩn, scope ALL (toàn công ty — bài mới) HOẶC scope ORG_UNIT thuộc phòng của user (bài cũ).
     * Sắp xếp ghim trước (FR-B02/B03) rồi mới nhất; phân trang theo lô (NFR-04).
     * Lọc nhanh tuỳ chọn theo phân loại (category ∈ NEWS/EVENT/ANNOUNCEMENT) và chủ đề (topic) — null = bỏ qua.
     */
    @Query("""
            select p from Post p
            where p.hidden = false
              and (p.scope = 'ALL' or (p.scope = 'ORG_UNIT' and p.orgUnitId in :unitIds))
              and (:category is null or p.category = :category)
              and (:topic is null or p.topic = :topic)
            order by p.pinned desc, p.createdAt desc
            """)
    List<Post> feed(@Param("unitIds") List<String> unitIds,
                    @Param("category") String category,
                    @Param("topic") String topic,
                    Pageable pageable);

    /** Admin xem mọi bài (kể cả ẩn) — quản lý/kiểm duyệt. */
    List<Post> findAllByOrderByPinnedDescCreatedAtDesc(Pageable pageable);

    /** Chặn trùng tin tự động (chúc mừng sinh nhật/onboard): bài có chứa marker ẩn trong body. */
    boolean existsByBodyContaining(String marker);
}
