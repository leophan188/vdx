package com.bpm.domain.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** Một lượt like của một người trên một bài (Story 2.5). Unique (post_id, user_id) — mỗi người 1 like/bài. */
@Entity
@Table(name = "social_like", uniqueConstraints = {
        @UniqueConstraint(name = "uk_like_post_user", columnNames = {"post_id", "user_id"})
})
public class PostLike {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "post_id", length = 36, nullable = false)
    private String postId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PostLike() {
    }

    public PostLike(String postId, String userId) {
        this.id = UUID.randomUUID().toString();
        this.postId = postId;
        this.userId = userId;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getPostId() { return postId; }
    public String getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
}
