package com.bpm.api.dto;

import com.bpm.domain.social.Post;
import com.bpm.domain.social.PostComment;
import com.bpm.domain.social.PostMedia;

import java.util.List;

/** DTO trả về cho bảng tin (Epic 2). */
public final class PostDto {

    private PostDto() {
    }

    public record MediaView(String id, String kind, String name, String contentType, long size, String url) {
        public static MediaView of(PostMedia m) {
            return new MediaView(m.getId(), m.getKind(), m.getOriginalName(), m.getContentType(),
                    m.getSizeBytes(), "/api/v1/media/" + m.getId());
        }
    }

    /** Người được @nhắc (resolve tên để FE tô sáng). */
    public record MentionView(String userId, String name) {
    }

    public record CommentView(String id, String authorId, String authorName, String body, boolean edited, boolean mine,
                              boolean editable, String createdAt, String parentId, List<MentionView> mentions) {
        public static CommentView of(PostComment c, boolean mine) {
            return of(c, mine, List.of());
        }

        public static CommentView of(PostComment c, boolean mine, List<MentionView> mentions) {
            return new CommentView(c.getId(), c.getAuthorId(), c.getAuthorName(), c.getBody(), c.isEdited(), mine,
                    mine && c.isEditable(), c.getCreatedAt().toString(), c.getParentId(),
                    mentions == null ? List.of() : mentions);
        }
    }

    public record PostView(String id, String authorId, String authorName, String body, String scope, String orgUnitId,
                           String topic, String category, String categoryLabel, String subjectUserId,
                           boolean pinned, boolean hidden, String createdAt,
                           long likeCount, boolean liked, long commentCount,
                           List<MediaView> media, List<CommentView> comments, List<MentionView> mentions) {

        public static PostView of(Post p, long likeCount, boolean liked, long commentCount,
                                  List<MediaView> media, List<CommentView> comments) {
            return of(p, likeCount, liked, commentCount, media, comments, List.of());
        }

        public static PostView of(Post p, long likeCount, boolean liked, long commentCount,
                                  List<MediaView> media, List<CommentView> comments, List<MentionView> mentions) {
            String cat = p.getCategory();
            return new PostView(p.getId(), p.getAuthorId(), p.getAuthorName(), p.getBody(), p.getScope(), p.getOrgUnitId(),
                    p.getTopic(), cat, categoryLabel(cat), p.getSubjectUserId(),
                    p.isPinned(), p.isHidden(), p.getCreatedAt().toString(),
                    likeCount, liked, commentCount, media, comments, mentions == null ? List.of() : mentions);
        }

        /** Nhãn tiếng Việt cho phân loại. */
        public static String categoryLabel(String c) {
            return switch (c == null ? "" : c) {
                case "NEWS" -> "Tin tức";
                case "EVENT" -> "Sự kiện";
                default -> "Thông báo";
            };
        }
    }
}
