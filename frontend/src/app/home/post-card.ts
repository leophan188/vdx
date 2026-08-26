import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { PostService, PostView, CommentView, MentionView } from '../core/post.service';
import { ToastService } from '../shared/toast/toast.service';
import { Avatar } from '../shared/avatar/avatar';
import { ImageLightbox } from '../shared/image-lightbox/image-lightbox';
import { MentionBox } from './mention-box';

/** Node cây bình luận: bình luận + danh sách reply (đệ quy) + cấp thụt lề. */
export interface CommentNode extends CommentView {
  children: CommentNode[];
  depth: number;
}

/**
 * Thẻ bài viết bảng tin (Epic 2): hiển thị nội dung + ảnh/video, like, bình luận nhiều cấp (reply lồng nhau,
 * sửa/xoá trong hạn), @mention tô sáng, ghim & ẩn/xoá (admin). Giữ phong cách ochome. Tự quản trạng thái cục bộ.
 */
@Component({
  selector: 'app-post-card',
  imports: [MentionBox, NgTemplateOutlet, Avatar, ImageLightbox],
  templateUrl: './post-card.html',
  styles: [`
    .ochome-comment--reply { margin-left: 38px; }
    .ochome-comment--reply.is-deep { margin-left: 0; }
    .ochome-comment__replybox { display: flex; gap: 8px; align-items: flex-start; margin: 6px 0 6px 38px; }
    .ochome-mention { color: var(--color-primary); font-weight: 600; white-space: nowrap; }
    /* Thiệp giữ dáng ĐỨNG căn giữa (xếp ngang trông lệch hẳn về trái), chỉ thu nhỏ mọi kích
       thước và khoảng cách để bỏ phần diện tích thừa — thẻ cũ cao gần 500px cho 2–3 dòng chữ. */
    .ochome-celebrate-hero { display: flex; flex-direction: column; align-items: center;
      gap: 6px; margin: 4px 0 10px; }
    /* Vòng quanh ảnh lấy màu của LOẠI tin (biến --cel-* đặt ở .ochome-post--bd/ob/anniv). */
    .ochome-celebrate-hero__ring { display: inline-flex; padding: 4px; border-radius: 50%;
      background: linear-gradient(135deg, var(--cel-a, #db2777), var(--cel-b, #ea580c) 55%, var(--cel-c, #f59e0b));
      box-shadow: 0 8px 24px color-mix(in srgb, var(--cel-a, #db2777) 35%, transparent); }
    .ochome-celebrate-hero__img { width: 88px; height: 88px; border-radius: 50%; object-fit: cover;
      border: 2px solid var(--color-surface); display: block; }
    .ochome-celebrate-hero ::ng-deep .avatar { width: 88px !important; height: 88px !important; font-size: 31px !important;
      border: 2px solid var(--color-surface); border-radius: 50%; }
    .ochome-celebrate-hero__name { font-size: 18px; font-weight: 800; color: var(--color-text); letter-spacing: .2px; }
    /* Phòng ban của người được chúc — chip nhỏ dưới tên, giúp nhận ra ngay người của bộ phận nào. */
    .ochome-celebrate-hero__dept { margin-top: -2px; padding: 2px 10px; border-radius: 999px;
      font-size: 12px; font-weight: 700; letter-spacing: .3px;
      color: var(--cel-a, var(--color-text-muted));
      background: color-mix(in srgb, var(--cel-a, currentColor) 14%, transparent);
      border: 1px solid color-mix(in srgb, var(--cel-a, currentColor) 35%, transparent); }
    /* Huy hiệu SỐ NĂM gắn bó — điểm nhấn riêng của thiệp thâm niên, đọc được từ xa. */
    .ochome-celebrate-hero__badge { display: inline-flex; align-items: baseline; gap: 4px;
      margin-top: 0; padding: 4px 13px; border-radius: 999px;
      background: linear-gradient(135deg, var(--cel-a), var(--cel-b) 60%, var(--cel-c));
      color: #fff; font-weight: 800; letter-spacing: .4px; text-shadow: 0 1px 2px rgba(0,0,0,.2);
      box-shadow: 0 6px 18px color-mix(in srgb, var(--cel-a) 35%, transparent); }
    .ochome-celebrate-hero__badge b { font-size: 17px; line-height: 1; }
    /* Nội dung căn giữa như thiệp, nhưng khung chữ rộng hơn cũ (580 → 720px) và dòng sát hơn
       để chữ không bị bó thành nhiều dòng ngắn giữa một thẻ rộng. */
    .ochome-post--celebrate .ochome-post__text { text-align: center; max-width: 720px;
      margin: 0 auto 2px; line-height: 1.55; }
  `]
})
export class PostCard {
  private api = inject(PostService);
  private toast = inject(ToastService);
  private sanitizer = inject(DomSanitizer);

  // độ sâu tối đa còn được thụt thêm lề (cấp sâu hơn vẫn nest nhưng không thụt nữa)
  private readonly MAX_INDENT_DEPTH = 3;

  readonly post = input.required<PostView>();
  readonly isAdmin = input<boolean>(false);
  readonly changed = output<void>(); // báo cha refresh khi xoá bài

  // bản sao cục bộ để cập nhật like/comment không cần reload cả feed
  readonly model = signal<PostView | null>(null);
  readonly current = computed(() => this.model() ?? this.post());

  /**
   * Bài CHÚC MỪNG nổi bật (sinh nhật / onboarding do hệ thống tạo):
   * body bắt đầu bằng 🎂 / 🎉, hoặc chứa marker '#celebrate', hoặc EVENT + đã ghim.
   */
  readonly isCelebration = computed(() => {
    const p = this.current();
    const body = (p.body ?? '').trimStart();
    return body.startsWith('🎂') || body.startsWith('🎉')
      || body.includes('#celebrate')
      || (p.category === 'EVENT' && p.pinned);
  });

  /**
   * LOẠI tin chúc mừng, để mỗi loại có nhận diện riêng thay vì dùng chung một khung hồng-cam.
   * Ưu tiên marker hệ thống (#celebrate-bd / -ob / -anniv do HrHighlightsService gắn); bài cũ hoặc
   * bài đăng tay không có marker thì đoán theo chữ trong nội dung.
   */
  readonly celebrationKind = computed<'bd' | 'ob' | 'anniv' | null>(() => {
    if (!this.isCelebration()) {
      return null;
    }
    const body = this.current().body ?? '';
    if (/#celebrate-bd-/i.test(body)) return 'bd';
    if (/#celebrate-ob-/i.test(body)) return 'ob';
    if (/#celebrate-anniv-/i.test(body)) return 'anniv';
    if (/sinh nhật/i.test(body)) return 'bd';
    if (/gia nhập|onboard|chào mừng/i.test(body)) return 'ob';
    if (/thâm niên|tri ân|kỷ niệm|gắn bó/i.test(body)) return 'anniv';
    return null;
  });

  /** Nhãn dải băng đầu bài theo loại. */
  readonly celebrationLabel = computed(() => {
    switch (this.celebrationKind()) {
      case 'bd': return '🎂 Chúc mừng sinh nhật';
      case 'ob': return '🌱 Chào thành viên mới';
      case 'anniv': return '🏆 Tri ân thâm niên';
      default: return '🎉 Chúc mừng · Sự kiện';
    }
  });

  /**
   * Tên nhân sự được chúc (cho avatar chữ-cái khi chưa có ảnh) — trích từ nội dung tin chúc mừng.
   * "🎂 Chúc mừng sinh nhật <Tên> — ..." / "🎉 Chào mừng <Tên> gia nhập ...". Rỗng nếu không nhận ra.
   */
  readonly celebrantName = computed(() => {
    const body = (this.current().body ?? '').replace(/​/g, '');
    const bd = body.match(/sinh nhật\s+([^—!]+?)\s*(?:—|!)/i);
    if (bd) return bd[1].trim();
    const ob = body.match(/Chào mừng\s+([^!]+?)\s+gia nhập/i);
    if (ob) return ob[1].trim();
    // Thâm niên: "🏆 Tri ân 5 năm gắn bó — Nguyễn Văn A (PDX.1)!" — trước đây không bắt nên thiệp thiếu tên.
    const an = body.match(/gắn bó\s*—\s*([^(!]+?)\s*(?:\(|!)/i);
    if (an) return an[1].trim();
    return '';
  });

  /** Số năm gắn bó (chỉ tin thâm niên) — dựng huy hiệu nổi bật trên thiệp. 0 = không phải tin này. */
  readonly anniversaryYears = computed(() => {
    if (this.celebrationKind() !== 'anniv') {
      return 0;
    }
    const m = (this.current().body ?? '').match(/Tri ân\s+(\d+)\s+năm/i);
    return m ? Number(m[1]) : 0;
  });

  /**
   * Phòng ban của nhân sự được chúc — trích từ chính nội dung tin (backend đã ghép sẵn),
   * nên không phải gọi thêm API. Bỏ qua chữ "công ty" vì đó chỉ là phương án dự phòng khi thiếu mã bộ phận.
   */
  readonly celebrantDept = computed(() => {
    const body = (this.current().body ?? '').replace(/\u200b/g, '');
    let m: RegExpMatchArray | null = null;
    switch (this.celebrationKind()) {
      case 'bd': m = body.match(/sinh nhật\s+[^—!]+—\s*([^!]+?)\s*!/i); break;
      case 'ob': m = body.match(/gia nhập\s+([^!]+?)\s*!/i); break;
      case 'anniv': m = body.match(/gắn bó\s*—[^(!]*\(([^)]+)\)/i); break;
      default: return '';
    }
    const dept = m ? m[1].trim() : '';
    return /^công ty$/i.test(dept) ? '' : dept;
  });

  readonly showComments = signal(false);
  readonly draft = signal('');
  readonly draftMentions = signal<string[]>([]);
  /** Chặn gửi trùng: Enter + click (hoặc bấm 2 lần) trong lúc POST chưa trả về → tạo 2 bình luận. */
  readonly sendingComment = signal(false);
  readonly sendingReply = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly editDraft = signal('');

  // reply inline: id bình luận cha đang mở ô trả lời + nội dung + mentions
  readonly replyingTo = signal<string | null>(null);
  readonly replyDraft = signal('');
  readonly replyMentions = signal<string[]>([]);

  // Cây bình luận dựng từ danh sách phẳng theo parentId.
  readonly commentTree = computed<CommentNode[]>(() => this.buildTree(this.current().comments));

  private buildTree(flat: CommentView[]): CommentNode[] {
    // byId khử trùng id (nếu list lỡ có comment trùng do race optimistic-append + reload).
    const byId = new Map<string, CommentNode>();
    for (const c of flat) byId.set(c.id, { ...c, children: [], depth: 0 });
    const roots: CommentNode[] = [];
    // DUYỆT NODE DUY NHẤT (byId.values) — KHÔNG duyệt flat (tránh push trùng khi flat có id lặp).
    for (const node of byId.values()) {
      const parent = node.parentId ? byId.get(node.parentId) : undefined;
      if (parent && parent !== node) {
        node.depth = Math.min(parent.depth + 1, this.MAX_INDENT_DEPTH);
        parent.children.push(node);
      } else {
        roots.push(node);
      }
    }
    return roots;
  }

  /** Tô sáng các đoạn "@Tên" khớp danh sách mentions → <span class="ochome-mention">. */
  /** Bỏ marker ẩn hệ thống (#celebrate-..., #demo-mxh) + ký tự zero-width khỏi nội dung hiển thị. */
  private stripMarkers(text: string): string {
    return (text ?? '')
      .replace(/[​‌﻿]*#(celebrate-[a-z]+-[\w.-]+|demo-mxh)\b/gi, '')
      .replace(/[ \t]+$/gm, '')
      .trimEnd();
  }

  renderBody(text: string, mentions: MentionView[]): SafeHtml {
    let html = this.escapeHtml(this.stripMarkers(text));
    const names = (mentions ?? []).map((m) => m.name).filter(Boolean)
      .sort((a, b) => b.length - a.length); // tên dài trước để khớp ưu tiên
    for (const name of names) {
      const re = new RegExp('@' + this.escapeRegex(this.escapeHtml(name)), 'g');
      html = html.replace(re, `<span class="ochome-mention">@${this.escapeHtml(name)}</span>`);
    }
    return this.sanitizer.bypassSecurityTrustHtml(html);
  }

  private escapeHtml(s: string): string {
    return (s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }
  private escapeRegex(s: string): string {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  // ===== Media: lưới tối đa 4 ô + xem ảnh lớn (lightbox) =====
  readonly mediaList = computed(() => this.current().media);
  readonly tiles = computed(() => this.mediaList().slice(0, 5)); // collage kiểu FB: tối đa 5 ô
  readonly tileCount = computed(() => Math.min(5, this.mediaList().length)); // 1..5 -> class bố cục
  readonly moreCount = computed(() => Math.max(0, this.mediaList().length - 5)); // số ảnh dư -> overlay "+N"
  readonly lightboxIndex = signal<number | null>(null);
  readonly lightboxItem = computed(() => {
    const i = this.lightboxIndex();
    return i === null ? null : (this.mediaList()[i] ?? null);
  });
  openLightbox(i: number): void { this.lightboxIndex.set(i); }
  closeLightbox(): void { this.lightboxIndex.set(null); }
  prevMedia(): void {
    const n = this.mediaList().length;
    this.lightboxIndex.update((i) => (i === null ? null : (i - 1 + n) % n));
  }
  nextMedia(): void {
    const n = this.mediaList().length;
    this.lightboxIndex.update((i) => (i === null ? null : (i + 1) % n));
  }

  // Ảnh đại diện theo userId (null nếu thiếu userId hoặc ảnh tải lỗi → rơi về chữ cái).
  private readonly brokenAvatars = signal<Set<string>>(new Set());
  avatarUrl(userId: string | null | undefined): string | null {
    if (!userId || this.brokenAvatars().has(userId)) return null;
    return `/api/v1/me/avatar/${userId}`;
  }
  onAvatarError(userId: string | null | undefined): void {
    if (!userId) return;
    this.brokenAvatars.update((s) => new Set(s).add(userId));
  }

  initials(name: string): string {
    const p = (name || '').trim().split(/\s+/);
    return ((p[0]?.[0] ?? '') + (p.length > 1 ? p[p.length - 1][0] : '')).toUpperCase();
  }
  tone(name: string): string {
    const tones = ['blue', 'purple', 'pink', 'green', 'orange', 'indigo'];
    let h = 0;
    for (const c of name || '') h = (h + c.charCodeAt(0)) % tones.length;
    return tones[h];
  }
  fmt(iso: string): string {
    return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }

  private patch(p: Partial<PostView>): void {
    this.model.set({ ...this.current(), ...p });
  }

  toggleLike(): void {
    const id = this.current().id;
    this.api.toggleLike(id).subscribe({
      next: (r) => this.patch({ liked: r.liked, likeCount: r.likeCount }),
      error: () => this.toast.error('Không thực hiện được')
    });
  }

  toggleComments(): void {
    const next = !this.showComments();
    this.showComments.set(next);
    if (next && this.current().comments.length === 0 && this.current().commentCount > 0) {
      this.api.comments(this.current().id).subscribe({
        next: (cs) => this.patch({ comments: cs }),
        error: () => this.toast.error('Không tải được bình luận')
      });
    }
  }

  /** Thêm 1 comment vào list — BỎ QUA nếu id đã tồn tại (tránh trùng do race optimistic + reload). */
  private appendComment(c: CommentView): void {
    const list = this.current().comments;
    if (list.some((x) => x.id === c.id)) return; // đã có (list vừa reload kèm nó) → không thêm lại
    this.patch({ comments: [...list, c], commentCount: this.current().commentCount + 1 });
  }

  submitComment(): void {
    const body = this.draft().trim();
    if (!body || this.sendingComment()) return;      // đang gửi → bỏ qua lần gọi thứ 2 (Enter + click)
    this.sendingComment.set(true);
    this.api.addComment(this.current().id, body, null, this.draftMentions()).subscribe({
      next: (c) => {
        this.appendComment(c);
        this.draft.set('');
        this.draftMentions.set([]);
        this.sendingComment.set(false);
      },
      error: () => { this.sendingComment.set(false); this.toast.error('Không gửi được bình luận'); }
    });
  }

  // ===== Reply (bình luận nhiều cấp) =====
  startReply(c: CommentView): void {
    this.replyingTo.set(c.id);
    this.replyDraft.set('');
    this.replyMentions.set([]);
  }
  cancelReply(): void {
    this.replyingTo.set(null);
    this.replyDraft.set('');
    this.replyMentions.set([]);
  }
  submitReply(parent: CommentView): void {
    const body = this.replyDraft().trim();
    if (!body || this.sendingReply()) return;        // chặn gửi trùng (Enter + click)
    this.sendingReply.set(true);
    this.api.addComment(this.current().id, body, parent.id, this.replyMentions()).subscribe({
      next: (c) => {
        this.appendComment(c);
        this.cancelReply();
        this.sendingReply.set(false);
      },
      error: () => { this.sendingReply.set(false); this.toast.error('Không gửi được trả lời'); }
    });
  }

  startEdit(c: CommentView): void {
    this.editingId.set(c.id);
    this.editDraft.set(c.body);
  }
  cancelEdit(): void {
    this.editingId.set(null);
    this.editDraft.set('');
  }
  saveEdit(c: CommentView): void {
    const body = this.editDraft().trim();
    if (!body) return;
    this.api.editComment(c.id, body).subscribe({
      next: (u) => {
        this.patch({ comments: this.current().comments.map((x) => (x.id === c.id ? u : x)) });
        this.cancelEdit();
      },
      error: (e) => this.toast.error('Không sửa được', e?.error?.message)
    });
  }
  deleteComment(c: CommentView): void {
    if (!window.confirm('Xoá bình luận này?')) return;
    const call = this.isAdmin() && !c.mine ? this.api.deleteCommentAdmin(c.id) : this.api.deleteOwnComment(c.id);
    call.subscribe({
      next: () => this.patch({
        comments: this.current().comments.filter((x) => x.id !== c.id),
        commentCount: Math.max(0, this.current().commentCount - 1)
      }),
      error: (e) => this.toast.error('Không xoá được', e?.error?.message)
    });
  }

  // ===== Admin =====
  togglePin(): void {
    const cur = this.current();
    this.api.pin(cur.id, !cur.pinned).subscribe({
      next: () => { this.patch({ pinned: !cur.pinned }); this.changed.emit(); },
      error: () => this.toast.error('Không ghim được')
    });
  }
  hidePost(): void {
    const cur = this.current();
    this.api.hidePost(cur.id, !cur.hidden).subscribe({
      next: () => { this.patch({ hidden: !cur.hidden }); this.changed.emit(); },
      error: () => this.toast.error('Không ẩn được bài')
    });
  }
  deletePost(): void {
    if (!window.confirm('Xoá hẳn bài viết này?')) return;
    this.api.deletePost(this.current().id).subscribe({
      next: () => { this.toast.success('Đã xoá bài'); this.changed.emit(); },
      error: () => this.toast.error('Không xoá được bài')
    });
  }
}
