import { Component, inject, output, signal } from '@angular/core';
import { PostService, MediaView, PostCategory } from '../core/post.service';
import { ToastService } from '../shared/toast/toast.service';
import { MentionBox } from './mention-box';

/**
 * Composer đăng bài (CHỈ admin) — Story 2.1. Bài đăng luôn cho TOÀN CÔNG TY (đã bỏ phạm vi phòng ban).
 * Nội dung + upload ảnh/video + PHÂN LOẠI (Tin tức/Sự kiện/Thông báo) + chủ đề. Hỗ trợ @mention. Phong cách ochome.
 */
@Component({
  selector: 'app-post-composer',
  imports: [MentionBox],
  templateUrl: './post-composer.html'
})
export class PostComposer {
  private api = inject(PostService);
  private toast = inject(ToastService);

  readonly created = output<void>();

  readonly open = signal(false);
  readonly body = signal('');
  readonly category = signal<PostCategory>('ANNOUNCEMENT');
  readonly topic = signal('');
  readonly uploaded = signal<MediaView[]>([]);
  readonly uploading = signal(false);
  readonly submitting = signal(false);
  readonly mentionIds = signal<string[]>([]);

  expand(): void {
    this.open.set(true);
  }

  onFiles(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    if (!files.length) return;
    this.uploading.set(true);
    let pending = files.length;
    for (const f of files) {
      this.api.uploadMedia(f).subscribe({
        next: (m) => { this.uploaded.update((l) => [...l, m]); if (--pending === 0) this.uploading.set(false); },
        error: (e) => { this.toast.error('Tải media lỗi', e?.error?.message || f.name); if (--pending === 0) this.uploading.set(false); }
      });
    }
    input.value = '';
  }

  removeMedia(id: string): void {
    this.uploaded.update((l) => l.filter((m) => m.id !== id));
  }

  submit(): void {
    if (!this.body().trim() && this.uploaded().length === 0) {
      this.toast.warning('Cần nội dung hoặc media');
      return;
    }
    this.submitting.set(true);
    this.api.create({
      body: this.body().trim(),
      mediaIds: this.uploaded().map((m) => m.id),
      category: this.category(),
      topic: this.topic().trim() || null,
      mentionUserIds: this.mentionIds()
    }).subscribe({
      next: () => {
        this.toast.success('Đã đăng bài');
        this.reset();
        this.created.emit();
      },
      error: (e) => { this.toast.error('Không đăng được', e?.error?.message); this.submitting.set(false); }
    });
  }

  reset(): void {
    this.body.set('');
    this.category.set('ANNOUNCEMENT');
    this.topic.set('');
    this.uploaded.set([]);
    this.mentionIds.set([]);
    this.submitting.set(false);
    this.open.set(false);
  }
}
