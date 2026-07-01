import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface MediaView {
  id: string;
  kind: 'IMAGE' | 'VIDEO';
  name: string;
  contentType: string;
  size: number;
  url: string;
}

/** Người được @nhắc (để FE tô sáng). */
export interface MentionView {
  userId: string;
  name: string;
}

/** Gợi ý @mention từ endpoint mention-suggest. */
export interface MentionSuggestion {
  userId: string;
  name: string;
}

export interface CommentView {
  id: string;
  authorId: string;
  authorName: string;
  body: string;
  edited: boolean;
  mine: boolean;
  editable: boolean;
  createdAt: string;
  parentId: string | null;
  mentions: MentionView[];
}

/** Phân loại bài đăng (toàn công ty): NEWS (Tin tức) | EVENT (Sự kiện) | ANNOUNCEMENT (Thông báo). */
export type PostCategory = 'NEWS' | 'EVENT' | 'ANNOUNCEMENT';

export interface PostView {
  id: string;
  authorId: string;
  authorName: string;
  body: string;
  scope: 'ALL' | 'ORG_UNIT';
  orgUnitId: string | null;
  topic: string | null;
  category: PostCategory;
  categoryLabel: string;
  subjectUserId: string | null;
  pinned: boolean;
  hidden: boolean;
  createdAt: string;
  likeCount: number;
  liked: boolean;
  commentCount: number;
  media: MediaView[];
  comments: CommentView[];
  mentions: MentionView[];
}

export interface CreatePostRequest {
  body: string;
  mediaIds: string[];
  category: PostCategory;
  topic?: string | null;
  mentionUserIds?: string[];
}

/** Bảng tin mạng xã hội nội bộ (Epic 2). Dùng phiên (cookie) → withCredentials. */
@Injectable({ providedIn: 'root' })
export class PostService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/posts';

  feed(opts?: { page?: number; size?: number; category?: string; topic?: string }): Observable<PostView[]> {
    const p = new URLSearchParams();
    p.set('page', String(opts?.page ?? 0));
    if (opts?.size) p.set('size', String(opts.size));
    if (opts?.category) p.set('category', opts.category);
    if (opts?.topic) p.set('topic', opts.topic);
    return this.http.get<PostView[]>(`${this.base}?${p.toString()}`, { withCredentials: true });
  }

  adminList(page = 0, size?: number): Observable<PostView[]> {
    const p = new URLSearchParams();
    p.set('page', String(page));
    if (size) p.set('size', String(size));
    return this.http.get<PostView[]>(`${this.base}/admin?${p.toString()}`, { withCredentials: true });
  }

  get(id: string): Observable<PostView> {
    return this.http.get<PostView>(`${this.base}/${id}`, { withCredentials: true });
  }

  /** Upload một media → trả descriptor (id để gắn vào create). */
  uploadMedia(file: File): Observable<MediaView> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<MediaView>(`${this.base}/media`, fd, { withCredentials: true });
  }

  create(req: CreatePostRequest): Observable<PostView> {
    return this.http.post<PostView>(this.base, req, { withCredentials: true });
  }

  pin(id: string, pinned: boolean): Observable<PostView> {
    return this.http.patch<PostView>(`${this.base}/${id}/pin`, { pinned }, { withCredentials: true });
  }

  toggleLike(id: string): Observable<{ likeCount: number; liked: boolean }> {
    return this.http.post<{ likeCount: number; liked: boolean }>(`${this.base}/${id}/like`, {}, { withCredentials: true });
  }

  comments(id: string): Observable<CommentView[]> {
    return this.http.get<CommentView[]>(`${this.base}/${id}/comments`, { withCredentials: true });
  }

  addComment(id: string, body: string, parentId: string | null = null, mentionUserIds: string[] = []): Observable<CommentView> {
    return this.http.post<CommentView>(`${this.base}/${id}/comments`, { body, parentId, mentionUserIds }, { withCredentials: true });
  }

  /** Gợi ý người để @mention (tối đa 8, account đang hoạt động). */
  mentionSuggest(q: string): Observable<MentionSuggestion[]> {
    const p = new URLSearchParams();
    p.set('q', q ?? '');
    return this.http.get<MentionSuggestion[]>(`${this.base}/mention-suggest?${p.toString()}`, { withCredentials: true });
  }

  editComment(commentId: string, body: string): Observable<CommentView> {
    return this.http.put<CommentView>(`${this.base}/comments/${commentId}`, { body }, { withCredentials: true });
  }

  deleteOwnComment(commentId: string): Observable<unknown> {
    return this.http.delete(`${this.base}/comments/${commentId}`, { withCredentials: true });
  }

  // ===== Kiểm duyệt (ADMIN) =====
  hidePost(id: string, hidden: boolean): Observable<unknown> {
    return this.http.patch(`${this.base}/${id}/hidden`, { hidden }, { withCredentials: true });
  }

  deletePost(id: string): Observable<unknown> {
    return this.http.delete(`${this.base}/${id}`, { withCredentials: true });
  }

  hideComment(commentId: string, hidden: boolean): Observable<unknown> {
    return this.http.patch(`${this.base}/comments/${commentId}/hidden`, { hidden }, { withCredentials: true });
  }

  deleteCommentAdmin(commentId: string): Observable<unknown> {
    return this.http.delete(`${this.base}/comments/${commentId}/admin`, { withCredentials: true });
  }
}
