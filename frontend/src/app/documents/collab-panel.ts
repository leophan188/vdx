import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ToastService } from '../shared/toast/toast.service';
import { CollabService, CommentItem, OpinionItem, RoundItem, RoundStatus } from '../core/collab.service';
import { DocumentService, DocSummary } from '../core/document.service';

/** Bảng cộng tác cho tài liệu (Story 3.11–3.17): bình luận, ý kiến phối hợp, ký & lưu trữ. */
@Component({
  selector: 'app-collab-panel',
  imports: [FormsModule],
  templateUrl: './collab-panel.html'
})
export class CollabPanel implements OnInit {
  readonly id = input.required<string>();
  private readonly type = 'DOCUMENT';

  private collab = inject(CollabService);
  private docSvc = inject(DocumentService);
  private toast = inject(ToastService);

  readonly tab = signal<'comments' | 'opinions' | 'signoff'>('comments');
  readonly doc = signal<DocSummary | null>(null);
  readonly comments = signal<CommentItem[]>([]);
  readonly opinions = signal<OpinionItem[]>([]);
  readonly rounds = signal<RoundItem[]>([]);
  readonly roundStatus = signal<Record<string, RoundStatus>>({});

  newComment = '';
  editingId: string | null = null;
  editBody = '';
  opStance = 'CO_Y_KIEN';
  opBody = '';
  coordParticipants = '';
  coordDeadline = 24;
  signNumber = '';
  archiveNo = '';
  archiveClass = 'Thường';
  archiveRetention = 10;

  readonly STANCES = [
    { v: 'DONG_Y', l: 'Đồng ý' }, { v: 'KHONG_DONG_Y', l: 'Không đồng ý' }, { v: 'CO_Y_KIEN', l: 'Có ý kiến' }
  ];

  ngOnInit(): void { this.reloadAll(); }

  reloadAll(): void {
    this.docSvc.get(this.id()).subscribe({ next: (d) => this.doc.set(d), error: () => {} });
    this.collab.comments(this.type, this.id()).subscribe({ next: (r) => this.comments.set(r), error: () => {} });
    this.collab.opinions(this.type, this.id()).subscribe({ next: (r) => this.opinions.set(r), error: () => {} });
    this.collab.rounds(this.type, this.id()).subscribe({
      next: (r) => { this.rounds.set(r); r.forEach((x) => this.refreshRound(x.id)); }, error: () => {}
    });
  }

  fmt(iso: string): string {
    return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }
  stanceLabel(v: string): string { return this.STANCES.find((s) => s.v === v)?.l ?? v; }

  // ---- Bình luận ----
  addComment(): void {
    const b = this.newComment.trim();
    if (!b) return;
    this.collab.addComment(this.type, this.id(), b).subscribe({
      next: () => { this.newComment = ''; this.toast.success('Đã gửi bình luận'); this.reloadAll(); },
      error: () => this.toast.error('Không gửi được bình luận')
    });
  }
  startEdit(c: CommentItem): void { this.editingId = c.id; this.editBody = c.body; }
  saveEdit(): void {
    if (!this.editingId) return;
    this.collab.editComment(this.editingId, this.editBody).subscribe({
      next: () => { this.editingId = null; this.toast.success('Đã sửa'); this.reloadAll(); },
      error: (e) => this.toast.error('Không sửa được', e?.error?.message)
    });
  }

  // ---- Ý kiến phối hợp ----
  giveOpinion(): void {
    if (!this.opBody.trim()) { this.toast.error('Nhập nội dung ý kiến'); return; }
    this.collab.giveOpinion(this.type, this.id(), this.opStance, this.opBody).subscribe({
      next: () => { this.opBody = ''; this.toast.success('Đã gửi ý kiến'); this.reloadAll(); },
      error: () => this.toast.error('Không gửi được ý kiến')
    });
  }
  resolve(o: OpinionItem, resolution: 'TIEP_THU' | 'KHONG_TIEP_THU'): void {
    const note = window.prompt(resolution === 'TIEP_THU' ? 'Ghi chú tiếp thu:' : 'Lý do không tiếp thu:', '') ?? '';
    this.collab.resolveOpinion(o.id, resolution, note).subscribe({
      next: () => { this.toast.success('Đã xử lý ý kiến'); this.reloadAll(); },
      error: () => this.toast.error('Không xử lý được')
    });
  }
  requestCoord(): void {
    const ps = this.coordParticipants.split(',').map((s) => s.trim()).filter(Boolean);
    if (!ps.length) { this.toast.error('Nhập username người phối hợp (phân tách dấu phẩy)'); return; }
    this.collab.requestCoordination(this.type, this.id(), ps, this.coordDeadline).subscribe({
      next: () => { this.coordParticipants = ''; this.toast.success('Đã gửi đề nghị phối hợp', ps.join(', ')); this.reloadAll(); },
      error: () => this.toast.error('Không gửi được đề nghị')
    });
  }
  refreshRound(rid: string): void {
    this.collab.roundStatus(rid).subscribe({ next: (s) => this.roundStatus.update((m) => ({ ...m, [rid]: s })), error: () => {} });
  }
  closeRound(rid: string): void {
    this.collab.closeRound(rid).subscribe({ next: () => { this.toast.success('Đã đóng đợt phối hợp'); this.reloadAll(); }, error: () => {} });
  }

  // ---- Ký & Lưu trữ ----
  sign(): void {
    if (!this.signNumber.trim()) { this.toast.error('Nhập số ký hiệu văn bản'); return; }
    this.docSvc.sign(this.id(), this.signNumber).subscribe({
      next: (d) => { this.doc.set(d); this.signNumber = ''; this.toast.success('Đã ghi nhận ký ban hành', d.signedNumber || ''); },
      error: (e) => this.toast.error('Không ký được', e?.error?.message)
    });
  }
  archive(): void {
    if (!this.archiveNo.trim()) { this.toast.error('Nhập số hồ sơ lưu trữ'); return; }
    this.docSvc.archive(this.id(), this.archiveNo, this.archiveClass, this.archiveRetention).subscribe({
      next: (d) => { this.doc.set(d); this.toast.success('Đã đóng & lưu trữ hồ sơ', d.archiveNo || ''); },
      error: (e) => this.toast.error('Không đóng hồ sơ được', e?.error?.message)
    });
  }
}
