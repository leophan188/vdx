import { Component, HostListener, computed, effect, inject, input, output, signal } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Modal } from '../../shared/modal/modal';
import { ImageLightbox, LightboxItem } from '../../shared/image-lightbox/image-lightbox';
import { SearchableSelect, SelectOption } from '../../shared/searchable-select/searchable-select';
import { memberPersonOptions } from '../../shared/person-options';
import { mergeBugFieldsIntoDescription } from '../../shared/bug-template';
import { ToastService } from '../../shared/toast/toast.service';
import {
  ProjectService, ProjectTask, TaskComment, TaskAttachment, TaskActivity, TaskActivityAction,
  TaskType, TaskStatus, TaskPriority, BugSeverity, TaskRequest, ProjectMember, WorkEntry, WorkRole, WorkLog
} from '../../core/project.service';
import { WorkEntryDialog } from '../work-entry/work-entry-dialog';
import { workRoleForTransition, workActionLabel, workActionColor } from '../work-stats';
import { DescShot } from '../desc-editor/desc-editor';
import { DescView } from '../desc-editor/desc-view';

type SubTab = 'info' | 'comments' | 'activity';

/**
 * Chi tiết Task kiểu Jira (modal rộng <app-modal [xwide]>).
 * - Header: code + title + badge type/priority + thanh % hoàn thành (progressPct).
 * - Hàng segmented 5 trạng thái (Backlog/Cần làm/Đang làm/Kiểm thử/Hoàn thành) → updateTaskStatus.
 * - Thông tin: assignee (searchable-select → assignTask), estimate, ngày, mô tả.
 * - Bình luận (list TaskComment + thêm/sửa/xoá, Enter gửi).
 * - Ảnh đính kèm (lưới thumbnail, lightbox, upload, xoá).
 *
 * Dùng: <app-prj-task-detail [projectId]="pid" [task]="t" [open]="o"
 *          (closed)="…" (changed)="reload()" />
 */
@Component({
  selector: 'app-prj-task-detail',
  imports: [Modal, SearchableSelect, ImageLightbox, WorkEntryDialog, DescView],
  templateUrl: './task-detail.html',
  styles: [`
    /* Ô tích BỎ QUA KIỂM THỬ — khối tuỳ chọn có viền, bấm cả khối là chọn.
       Dùng margin thay flex-gap và ghim kích thước bằng !important vì input trong form
       dính quy tắc width:100% dùng chung, làm ô vuông phình và chữ tràn lệch hàng. */
    .td__skip { display: flex; align-items: flex-start; padding: 9px 11px;
      border: 1px solid var(--color-border); border-radius: var(--radius-md);
      background: var(--color-surface); cursor: pointer; transition: border-color .15s ease; }
    .td__skip:hover { border-color: var(--color-primary); }
    .td__skip:has(input:checked) { border-color: var(--color-primary);
      background: color-mix(in srgb, var(--color-primary) 8%, transparent); }
    .td__skip input[type="checkbox"] { flex: 0 0 16px !important; width: 16px !important;
      height: 16px !important; min-width: 16px; max-width: 16px; margin: 1px 10px 0 0 !important;
      padding: 0 !important; cursor: pointer; accent-color: var(--color-primary); }
    .td__skip input:disabled { cursor: default; opacity: .55; }
    .td__skip-txt { display: grid; gap: 2px; min-width: 0; }
    .td__skip-txt b { font-size: var(--text-sm); font-weight: var(--weight-semibold); }
    .td__skip-txt i { font-style: normal; font-size: var(--text-xs); color: var(--color-text-muted); line-height: 1.45; }
    .td { display: grid; gap: var(--space-4); width: 100%; }
    .td__head { display: grid; gap: var(--space-2); }
    .td__path { display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
      padding-bottom: var(--space-2); border-bottom: 1px dashed var(--color-border); }
    .td__path-label { font-size: var(--text-xs); color: var(--color-text-muted); }
    .td__path-badge { font-size: 10px; padding: 0 6px; line-height: 17px; }
    .td__path-name { font-size: var(--text-sm); color: var(--color-text); max-width: 280px;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .td__path-sep { color: var(--color-text-muted); }
    .td__titlerow { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; }
    .td__code { font-weight: 700; color: var(--color-primary); }
    .td__title { font-size: 1.05rem; font-weight: 700; }
    .td__progress { display: flex; align-items: center; gap: var(--space-2); }
    .td__bar { flex: 1; height: 8px; border-radius: 999px; background: var(--color-surface-alt); overflow: hidden; }
    .td__bar > i { display: block; height: 100%; background: var(--color-primary); transition: width .2s; }
    .td__pct { font-size: .8rem; font-weight: 600; min-width: 38px; text-align: right; }

    /* Khu Vòng đời (thao tác nhanh + đổi PIC) */
    .td__life { display: grid; gap: var(--space-2); padding: 12px; border-radius: 10px;
      background: var(--color-surface-alt); border: 1px solid var(--color-border); }
    .td__life-head { display: flex; align-items: center; gap: 8px; }
    .td__life-head h4 { margin: 0; font-size: .9rem; }
    .td__life-hint { font-size: .78rem; color: var(--color-text-muted); }
    .td__life-acts { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
    .td__life-pick { display: grid; gap: 8px; padding-top: var(--space-2);
      border-top: 1px dashed var(--color-border); }
    .td__life-pick searchable-select { min-width: 260px; }
    .td__life-pickrow { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }

    /* Khu GIỜ LÀM VIỆC (timesheet) */
    .td__work { display: grid; gap: 8px; padding: 12px; border-radius: 10px;
      background: var(--color-surface-alt); border: 1px solid var(--color-border); }
    .td__work-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .td__work-head h4 { margin: 0; font-size: .9rem; }
    .td__work-sum { font-size: .8rem; color: var(--color-text-muted); }
    .td__work-sum b { color: var(--color-text); font-variant-numeric: tabular-nums; }
    .td__work-var { color: var(--status-done); }
    .td__work-var.is-over { color: var(--overdue, #e5484d); }
    .td__work-list { display: grid; gap: 2px; }
    /* align-items: start — ghi chú dài xuống nhiều dòng thì các cột còn lại bám mép trên,
       không bị đẩy trôi xuống giữa dòng cao. */
    .td__work-row { display: grid; grid-template-columns: 114px minmax(90px, 1fr) 92px 52px 2fr 52px;
      align-items: start; gap: 8px; padding: 6px 8px; border-radius: 6px;
      background: var(--color-surface); font-size: var(--text-sm); }
    .td__work-role { font-size: 10px; font-weight: 700; padding: 1px 6px; border-radius: 999px; text-align: center;
      white-space: nowrap; color: var(--act, var(--status-active));
      background: color-mix(in srgb, var(--act, var(--status-active)) 14%, transparent); }
    .td__work-who { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .td__work-date { color: var(--color-text-muted); font-size: var(--text-xs); font-variant-numeric: tabular-nums; }
    .td__work-h { text-align: right; font-variant-numeric: tabular-nums; }
    /* Ghi chú (lý do reopen, mô tả việc đã làm) hiện ĐỦ chữ: cắt bằng "…" thì phần quan trọng nhất
       — lý do bị trả lại — nằm ở cuối câu và không đọc được, phải rê chuột mới thấy. */
    .td__work-note { color: var(--color-text-muted); font-size: var(--text-xs);
      white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.45; }
    .td__work-acts { display: flex; gap: 6px; align-items: center; justify-content: flex-end; }
    .td__work-del { border: 0; background: none; cursor: pointer; color: var(--color-text-muted);
      font-size: 1.1rem; line-height: 1; padding: 0; }
    /* Ô chọn người khi nắn lại dòng giờ ghi nhầm */
    .td__work-reassign { display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
      margin: 2px 0 6px; padding: 8px 10px; border-radius: 6px;
      background: var(--color-surface); border: 1px dashed var(--color-primary); }
    .td__work-reassign searchable-select { min-width: 240px; }
    .td__work-del:hover { color: var(--overdue, #e5484d); }
    .td__work-empty { margin: 0; font-size: var(--text-xs); color: var(--color-text-muted); line-height: 1.5; }
    .td__life-input { height: var(--control-h-sm); border: 1px solid var(--color-border);
      border-radius: var(--radius-md); background: var(--color-surface); color: var(--color-text);
      padding: 0 var(--space-2); font: inherit; width: 130px; }
    .td__life-note { font-size: .78rem; color: var(--color-text-muted); }

    .td__seg { display: flex; flex-wrap: wrap; gap: 6px; }
    .td__seg button {
      border: 1px solid var(--color-border); background: var(--color-surface);
      padding: 6px 12px; border-radius: 999px; font-size: .82rem; cursor: pointer; color: var(--color-text);
    }
    .td__seg button:hover { background: var(--color-surface-alt); }
    .td__seg button.is-active { background: var(--color-primary); border-color: var(--color-primary); color: var(--color-text-invert); font-weight: 600; }
    .td__seg button:disabled { opacity: .6; cursor: default; }

    .td__grid { display: grid; grid-template-columns: 1.2fr 1fr; gap: var(--space-4); }
    @media (max-width: 720px) { .td__grid { grid-template-columns: 1fr; } }
    .td__info { display: grid; gap: var(--space-3); }
    .td__row { display: grid; grid-template-columns: 120px 1fr; align-items: center; gap: var(--space-2); }
    .td__row > label { font-size: .82rem; color: var(--color-text-muted); }
    /* Mô tả do <app-desc-view> vẽ (chữ + ảnh trong dòng) — chỉ cần cho nó thành khối. */
    .td__desc { display: block; }
    .td__muted { color: var(--color-text-muted); }

    .td__sec h4 { margin: 0 0 var(--space-2); font-size: .9rem; }

    /* Chi tiết lỗi (BUG/ISSUE) */
    .bug { display: grid; gap: var(--space-3); padding: 12px; border-radius: 10px;
           background: var(--color-surface-alt); border: 1px solid var(--color-border); }
    .bug__head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); }
    .bug__head h4 { margin: 0; font-size: .9rem; }
    .bug__field { display: grid; gap: 4px; }
    .bug__field > label { font-size: .78rem; color: var(--color-text-muted); font-weight: 600; }
    .bug__val { font-size: .88rem; white-space: pre-wrap; line-height: 1.45; color: var(--color-text); }
    .bug__val.is-empty { color: var(--color-text-muted); font-style: italic; }
    .bug__edit { display: grid; gap: var(--space-3); }
    .bug__edit textarea, .bug__edit input {
      width: 100%; padding: 7px 9px; border: 1px solid var(--color-border); border-radius: 8px;
      background: var(--color-surface); color: var(--color-text); font: inherit; box-sizing: border-box;
    }
    .bug__acts { display: flex; gap: 8px; }
    .sev { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: .72rem; font-weight: 600; line-height: 18px; }
    .sev--red { background: var(--overdue-bg); color: var(--overdue); }
    .sev--orange { background: var(--status-pending-bg); color: var(--status-pending); }
    .sev--gray { background: var(--color-surface); color: var(--color-text-muted); }

    /* Sub-tabs (Thông tin | Bình luận | Hoạt động) */
    .td__tabs { display: flex; gap: 4px; border-bottom: 1px solid var(--color-border); }
    .td__tab {
      border: none; background: none; padding: 8px 14px; cursor: pointer; font-size: .85rem;
      color: var(--color-text-muted); border-bottom: 2px solid transparent; margin-bottom: -1px;
    }
    .td__tab:hover { color: var(--color-text); }
    .td__tab.is-active { color: var(--color-primary); border-bottom-color: var(--color-primary); font-weight: 600; }
    .td__tab small { font-weight: 400; opacity: .75; }


    /* Hoạt động (timeline kiểu Jira) */
    .td__acts { display: grid; gap: 0; max-height: 420px; overflow: auto; }
    .act { display: grid; grid-template-columns: 32px 1fr; gap: 10px; padding: 8px 0; position: relative; }
    .act:not(:last-child)::before {
      content: ''; position: absolute; left: 15px; top: 34px; bottom: -8px; width: 2px; background: var(--color-border);
    }
    .act__icon {
      width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
      background: var(--color-surface-alt); font-size: .95rem; z-index: 1;
    }
    .act__icon.is-status { background: #dbeafe; } .act__icon.is-assign { background: #ede9fe; }
    .act__icon.is-comment { background: #dcfce7; } .act__icon.is-attach { background: #fef3c7; }
    .act__icon.is-spent { background: #cffafe; } .act__icon.is-created { background: #e2e8f0; }
    .act__icon.is-edit { background: #fce7f3; }
    .act__body { display: grid; gap: 2px; align-content: start; }
    .act__line { font-size: .86rem; }
    .act__label { font-weight: 600; }
    .act__detail { color: var(--color-text); }
    .act__meta { font-size: .74rem; color: var(--color-text-muted); }

    /* Bình luận */
    .td__comments { display: grid; gap: var(--space-2); max-height: 320px; overflow: auto; }
    .cmt { display: grid; gap: 4px; padding: 8px 10px; border-radius: 10px; background: var(--color-surface-alt); }
    .cmt__top { display: flex; align-items: baseline; gap: 6px; flex-wrap: wrap; }
    .cmt__author { font-weight: 600; font-size: .85rem; }
    .cmt__time { font-size: .72rem; color: var(--color-text-muted); }
    .cmt__edited { font-size: .72rem; font-style: italic; color: var(--color-text-muted); }
    .cmt__body { font-size: .88rem; white-space: pre-wrap; line-height: 1.4; }
    /* Ảnh ĐÃ gửi kèm bình luận */
    .cmt__imgs { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
    .cmt__imgs img { width: 96px; height: 96px; object-fit: cover; border-radius: 8px;
      border: 1px solid var(--color-border); display: block; cursor: zoom-in; }
    /* Ảnh CHỜ gửi (chưa upload) */
    .cmt__pending { display: flex; flex-wrap: wrap; gap: 8px; margin-top: var(--space-2); }
    .cmt__pend { position: relative; width: 72px; height: 72px; border-radius: 8px; overflow: hidden;
      border: 1px dashed var(--color-border); }
    .cmt__pend img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .cmt__pend-del { position: absolute; top: 2px; right: 2px; width: 20px; height: 20px; padding: 0;
      border: none; border-radius: 50%; background: rgba(0,0,0,.6); color: #fff; font-size: .7rem;
      line-height: 20px; cursor: pointer; display: flex; align-items: center; justify-content: center; }
    .cmt__acts { display: flex; gap: 4px; }
    /* Nút icon: vùng chạm 26px cho dễ bấm, mờ lúc thường để không tranh chú ý với nội dung
       bình luận, rõ lên khi rê chuột. */
    .cmt__ico { width: 26px; height: 26px; padding: 0; border: none; border-radius: var(--radius-sm, 6px);
      background: none; cursor: pointer; font-size: .82rem; line-height: 1; opacity: .55;
      display: inline-flex; align-items: center; justify-content: center; }
    .cmt__ico:hover { opacity: 1; background: var(--color-surface-alt); }
    .cmt__ico--danger:hover { background: var(--overdue-bg, #fee); }
    .linkbtn { background: none; border: none; padding: 0; cursor: pointer; color: var(--color-primary); font-size: .75rem; }
    .linkbtn--danger { color: var(--color-error); }
    .cmt__edit { display: grid; gap: 6px; }
    .cmt__editacts { display: flex; gap: 8px; }
    .td__newcmt { display: flex; gap: 8px; align-items: flex-start; margin-top: var(--space-2); }
    .td__newcmt textarea { flex: 1; }

    /* Ảnh */
    .td__atts { display: grid; grid-template-columns: repeat(auto-fill, minmax(96px, 1fr)); gap: 8px; }
    .att { position: relative; border-radius: 8px; overflow: hidden; aspect-ratio: 1; background: var(--color-surface-alt); }
    .att img { width: 100%; height: 100%; object-fit: cover; cursor: pointer; display: block; }
    .att__del {
      position: absolute; top: 4px; right: 4px; width: 22px; height: 22px; border-radius: 50%;
      border: none; background: rgba(0,0,0,.55); color: #fff; cursor: pointer; line-height: 1; font-size: .8rem;
    }
    .td__upload { margin-top: var(--space-2); }

    /* Lightbox */
    .lb { position: fixed; inset: 0; z-index: 2000; background: rgba(0,0,0,.82);
          display: flex; align-items: center; justify-content: center; }
    .lb img { max-width: 92vw; max-height: 88vh; object-fit: contain; }
    .lb__close { position: absolute; top: 18px; right: 22px; background: none; border: none; color: #fff; font-size: 1.8rem; cursor: pointer; }
    .lb__nav { position: absolute; top: 50%; transform: translateY(-50%); background: rgba(255,255,255,.15); color: #fff;
               border: none; width: 44px; height: 64px; font-size: 2rem; cursor: pointer; }
    .lb__nav--prev { left: 12px; } .lb__nav--next { right: 12px; }
    .lb__counter { position: absolute; bottom: 18px; left: 50%; transform: translateX(-50%); color: #fff; font-size: .85rem; }
  `]
})
export class PrjTaskDetail {
  private svc = inject(ProjectService);
  private toast = inject(ToastService);

  readonly projectId = input.required<string>();
  readonly task = input<ProjectTask | null>(null);
  readonly open = input(false);

  readonly closed = output<void>();
  readonly changed = output<void>();

  // Bản sao cục bộ của task để cập nhật ngay khi đổi status/assignee.
  readonly model = signal<ProjectTask | null>(null);
  readonly current = computed(() => this.model() ?? this.task());

  /** CHỈ thành viên dự án (không lấy toàn bộ nhân sự hệ thống). */
  readonly members = signal<ProjectMember[]>([]);
  readonly comments = signal<TaskComment[]>([]);
  readonly attachments = signal<TaskAttachment[]>([]);
  readonly activities = signal<TaskActivity[]>([]);

  /** Ảnh ĐÍNH KÈM CHUNG của task (ảnh của bình luận hiện dưới bình luận, không lẫn vào đây). */
  readonly taskAttachments = computed<TaskAttachment[]>(() =>
    this.attachments().filter((a) => !a.commentId));

  /** Ảnh đính kèm chung → phần tử cho lightbox chung (zoom được). Index khớp taskAttachments(). */
  readonly lightboxItems = computed<LightboxItem[]>(() =>
    this.taskAttachments().map((a) => ({ url: this.attUrl(a), name: a.fileName, kind: 'IMAGE' as const })));

  /**
   * Ảnh cấp cho phần Mô tả: "[Ảnh n]" ứng với ảnh đính kèm THỨ n, đúng thứ tự tải lên —
   * cùng quy ước với lúc nhập (ảnh dán được đánh số rồi upload theo đúng thứ tự đó).
   */
  readonly descShots = computed<DescShot[]>(() =>
    this.taskAttachments().map((a, i) => ({ no: i + 1, url: this.attUrl(a) })));

  /** Bấm ảnh trong Mô tả → mở lightbox; số thứ tự n ứng với vị trí n-1 của dải đính kèm. */
  openShotByNo(no: number): void {
    if (no >= 1 && no <= this.taskAttachments().length) this.lightboxIndex.set(no - 1);
  }

  readonly draft = signal('');
  /** Ảnh CHỜ trong ô soạn bình luận — upload sau khi bấm Gửi (cần commentId). */
  readonly cmtImages = signal<{ file: File; url: string }[]>([]);
  readonly sendingComment = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly editDraft = signal('');
  readonly busyStatus = signal(false);

  // ===== Vòng đời: chọn người kiểm thử khi chuyển IN_PROGRESS → IN_REVIEW =====
  readonly pickTesterOpen = signal(false);
  readonly testerUserId = signal<string>('');
  readonly busyLifecycle = signal(false);
  // Reopen (Kiểm thử → Đang làm) + chọn lại người thực hiện (dev sửa).
  readonly pickReopenOpen = signal(false);
  readonly reopenAssignee = signal<string>('');
  // Bắt đầu Đang làm: bắt buộc nhập est + ngày hoàn thành nếu còn thiếu.
  readonly pickStartOpen = signal(false);
  readonly startEst = signal<string>('');
  readonly startFromIso = signal<string>('');
  readonly startDueIso = signal<string>('');

  // Sub-tab đang xem.
  readonly tab = signal<SubTab>('info');


  readonly lightboxIndex = signal<number | null>(null);
  readonly lightboxItem = computed(() => {
    const i = this.lightboxIndex();
    return i === null ? null : (this.attachments()[i] ?? null);
  });

  readonly statusOptions: { value: TaskStatus; label: string }[] = [
    { value: 'BACKLOG', label: 'Backlog' }, { value: 'TODO', label: 'To Do' },
    { value: 'IN_PROGRESS', label: 'In Progress' }, { value: 'IN_REVIEW', label: 'Testing' },
    { value: 'DONE', label: 'Done' }, { value: 'CANCELLED', label: 'Cancelled' }
  ];
  readonly typeLabels: Record<TaskType, string> = {
    EPIC: 'Epic', STORY: 'Story', TASK: 'Task', SUBTASK: 'Subtask', BUG: 'Bug', ISSUE: 'Issue'
  };
  readonly priorityLabels: Record<TaskPriority, string> = {
    LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', URGENT: 'Urgent'
  };
  readonly severityLabels: Record<BugSeverity, string> = {
    BLOCKER: 'Blocker', CRITICAL: 'Critical', MAJOR: 'Major', MINOR: 'Minor', TRIVIAL: 'Trivial'
  };
  readonly severityOptions: { value: BugSeverity; label: string }[] = [
    { value: 'BLOCKER', label: 'Blocker' }, { value: 'CRITICAL', label: 'Critical' },
    { value: 'MAJOR', label: 'Major' }, { value: 'MINOR', label: 'Minor' },
    { value: 'TRIVIAL', label: 'Trivial' }
  ];
  readonly severitySel: SelectOption[] = this.severityOptions.map((o) => ({ value: o.value, label: o.label }));

  /** Task hiện tại có phải BUG/ISSUE không (hiển thị khối Chi tiết lỗi). */
  readonly isBug = computed(() => {
    const t = this.current();
    return !!t && (t.type === 'BUG' || t.type === 'ISSUE');
  });

  /**
   * Rollup = task TỰ TỔNG HỢP từ con: có con (leaf=false) HOẶC là EPIC/STORY.
   * → khoá ô nhập assignee / tester / est / ngày (read-only), hiển thị "Tự tổng hợp từ task con".
   */
  readonly isRollup = computed(() => {
    const t = this.current();
    return !!t && (!t.leaf || t.type === 'EPIC' || t.type === 'STORY');
  });

  // ===== Sửa inline khối Chi tiết lỗi =====
  readonly editingBug = signal(false);
  readonly busyBug = signal(false);
  bugForm: {
    severity: BugSeverity | null; stepsToReproduce: string;
    expectedResult: string; actualResult: string; environment: string;
  } = { severity: null, stepsToReproduce: '', expectedResult: '', actualResult: '', environment: '' };

  readonly activityLabels: Record<TaskActivityAction, string> = {
    CREATED: 'Tạo task', STATUS: 'Đổi trạng thái', ASSIGN: 'Gán người',
    EDIT: 'Sửa', COMMENT: 'Bình luận', ATTACH: 'Đính kèm ảnh', SPENT: 'Ghi giờ làm'
  };
  readonly activityIcons: Record<TaskActivityAction, string> = {
    CREATED: '✨', STATUS: '🔄', ASSIGN: '👤', EDIT: '✏️', COMMENT: '💬', ATTACH: '📎', SPENT: '⏱️'
  };

  /**
   * Người để gán việc. Bỏ người đã tạm ngưng, NHƯNG giữ lại người đang là người thực hiện / kiểm thử
   * của chính việc này — nếu bỏ luôn thì ô chọn hiện trống, nhìn như dữ liệu bị mất.
   */
  readonly peopleSel = computed<SelectOption[]>(() => {
    const t = this.current();
    const keep = t?.assigneeUserId || t?.testerUserId || null;
    return memberPersonOptions(this.members(), keep);
  });
  /** Riêng ô người kiểm thử giữ đúng người kiểm thử hiện tại. */
  readonly testerSel = computed<SelectOption[]>(() =>
    memberPersonOptions(this.members(), this.current()?.testerUserId ?? null));

  constructor() {
    // Khi task đầu vào đổi → reset bản sao cục bộ.
    effect(() => {
      const t = this.task();
      this.model.set(t);
    });

    // Khi mở (open=true & có task) → nạp comments + attachments + hoạt động; về tab Thông tin.
    effect(() => {
      const t = this.task();
      if (this.open() && t) { this.tab.set('info'); this.loadDetails(t.id); }
    });
  }

  private loadDetails(taskId: string): void {
    const pid = this.projectId();
    // Người thực hiện chỉ trong thành viên dự án.
    this.svc.listMembers(pid).subscribe({
      next: (m) => this.members.set(m),
      error: () => { /* bỏ qua */ }
    });
    this.svc.listComments(pid, taskId).subscribe({
      next: (c) => this.comments.set(c),
      error: () => this.comments.set([])
    });
    this.svc.listAttachments(pid, taskId).subscribe({
      next: (a) => this.attachments.set(a),
      error: () => this.attachments.set([])
    });
    this.loadActivity(taskId);
    this.loadWorkLogs(taskId);
  }

  private loadActivity(taskId: string): void {
    this.svc.listActivity(this.projectId(), taskId).subscribe({
      next: (a) => this.activities.set(a),
      error: () => this.activities.set([])
    });
  }

  // ===== Trạng thái (segmented) =====
  setStatus(s: TaskStatus): void {
    const t = this.current();
    if (!t || t.status === s || this.busyStatus()) return;
    // Chuyển ĐANG LÀM mà thiếu est/ngày → mở form nhập (thay vì để BE báo lỗi).
    if (s === 'IN_PROGRESS' && this.needsStartInfo(t)) { this.openStart(t); return; }
    // Mốc bàn giao (Kiểm thử / Hoàn thành) bắt buộc ghi giờ → hỏi trước khi gọi API.
    const role = workRoleForTransition(t, s);
    if (role) { this.askWork(role, (w) => this.doSetStatus(s, w), s === 'DONE'); return; }
    this.doSetStatus(s);
  }
  private doSetStatus(s: TaskStatus, work?: WorkEntry): void {
    const t = this.current();
    if (!t) return;
    this.busyStatus.set(true);
    this.svc.updateTaskStatus(this.projectId(), t.id, s, work).subscribe({
      next: (u) => {
        this.model.set(u);
        this.busyStatus.set(false);
        this.toast.success('Đã đổi trạng thái', u.code);
        this.changed.emit();
        this.loadActivity(u.id);
      },
      error: (e) => { this.busyStatus.set(false); this.toast.error('Không đổi được trạng thái', e?.error?.message ?? ''); }
    });
  }

  // ===== Assignee =====
  assign(userId: string | null): void {
    const t = this.current();
    if (!t) return;
    this.svc.assignTask(this.projectId(), t.id, userId || null).subscribe({
      next: (u) => {
        const before = t.assigneeUserId ?? null;
        this.model.set(u);
        this.toast.success('Đã gán người thực hiện', u.code);
        this.changed.emit();
        this.loadActivity(u.id);
        this.offerMoveLogs('DEV', before, u.assigneeUserId ?? null);
      },
      error: (e) => this.toast.error('Không gán được', e?.error?.message ?? '')
    });
  }

  /**
   * Sau khi đổi người của một vai, hỏi xem có nắn luôn các dòng giờ ĐÃ GHI của vai đó sang người mới không.
   *
   * Hai tình huống nhìn giống hệt nhau từ phía hệ thống: gán nhầm rồi sửa ngay (giờ phải theo người mới)
   * và bàn giao thật (giờ của người trước phải ở lại). Vì vậy KHÔNG tự chuyển — hỏi, và chỉ hỏi khi thực
   * sự có dòng giờ của người cũ, kèm số giờ để người dùng cân nhắc.
   */
  private offerMoveLogs(role: WorkRole, oldUserId: string | null, newUserId: string | null): void {
    const t = this.current();
    if (!t || !oldUserId || !newUserId || oldUserId === newUserId) {
      return;
    }
    const mine = this.workLogs().filter((w) => w.role === role && w.userId === oldUserId);
    if (!mine.length) {
      return;
    }
    const hours = Math.round(mine.reduce((sum, w) => sum + (w.hours || 0), 0) * 100) / 100;
    const oldName = mine[0].userName || 'người trước';
    const newName = this.peopleSel().find((o) => o.value === newUserId)?.label ?? 'người mới';
    const ok = confirm(`${mine.length} dòng giờ (${hours}h) của vai ${this.roleLabel(role)} đang ghi cho `
      + `${oldName}.\n\nChuyển sang ${newName}?\n\n`
      + `• Chọn OK nếu vừa gán nhầm người.\n`
      + `• Chọn Cancel nếu ${oldName} đã thực sự làm phần giờ đó rồi bàn giao lại.`);
    if (!ok) {
      return;
    }
    forkJoin(mine.map((w) => this.svc.reassignWorkLog(this.projectId(), w.id, newUserId)
      .pipe(catchError(() => of(null))))).subscribe(() => {
        this.toast.success('Đã chuyển giờ sang người mới', `${hours}h`);
        this.loadWorkLogs(t.id);
        this.changed.emit();
      });
  }

  // ===== Tester (người kiểm thử) — lưu qua updateTask (TaskRequest có testerUserId) =====
  setTester(userId: string | null): void {
    const t = this.current();
    if (!t) return;
    const body = this.buildRequest(t, { testerUserId: userId || null });
    this.svc.updateTask(this.projectId(), t.id, body).subscribe({
      next: (u) => {
        const before = t.testerUserId ?? null;
        this.model.set(u);
        this.toast.success('Đã gán người kiểm thử', u.code);
        this.changed.emit();
        this.loadActivity(u.id);
        this.offerMoveLogs('TEST', before, u.testerUserId ?? null);
      },
      error: (e) => this.toast.error('Không gán được người kiểm thử', e?.error?.message ?? '')
    });
  }

  /** Dựng TaskRequest ĐẦY ĐỦ từ task hiện có (giữ mọi field) + ghi đè patch. */
  private buildRequest(t: ProjectTask, patch: Partial<TaskRequest> = {}): TaskRequest {
    return {
      parentId: t.parentId, title: t.title, description: t.description,
      type: t.type, status: t.status, priority: t.priority,
      assigneeUserId: t.assigneeUserId, estimateHours: t.estimateHours,
      startDate: t.startDate, dueDate: t.dueDate, orderIndex: t.orderIndex, screen: t.screen,
      severity: t.severity, stepsToReproduce: t.stepsToReproduce,
      expectedResult: t.expectedResult, actualResult: t.actualResult, environment: t.environment,
      testerUserId: t.testerUserId ?? null,
      skipTest: t.skipTest ?? false,
      ...patch
    };
  }

  // ===== Vòng đời (thao tác nhanh theo status) =====
  /** TODO → IN_PROGRESS: bắt đầu làm. THIẾU est/ngày → mở form nhập bắt buộc. */
  startProgress(): void {
    const t = this.current();
    if (!t || this.busyLifecycle()) return;
    if (!this.needsStartInfo(t)) { this.doStart(t); return; }
    this.openStart(t);
  }
  /**
   * Có phải nhập est + từ ngày → đến ngày trước khi Đang làm không? (khớp rule BE)
   * MIỄN: task cha (rollup tự tính) và Reopen từ Kiểm thử/Hoàn thành/Huỷ (việc của dev, đã ước lượng trước).
   */
  private needsStartInfo(t: ProjectTask): boolean {
    if (!t.leaf) return false;
    if (t.status === 'IN_REVIEW' || t.status === 'DONE' || t.status === 'CANCELLED') return false;
    return !(t.estimateHours > 0 && t.startDate && t.dueDate);
  }
  /** Thực hiện chuyển IN_PROGRESS (đã đủ điều kiện). */
  private doStart(t: ProjectTask): void {
    this.busyLifecycle.set(true);
    this.svc.updateTaskStatus(this.projectId(), t.id, 'IN_PROGRESS').subscribe({
      next: (u) => {
        this.model.set(u);
        this.busyLifecycle.set(false);
        this.pickStartOpen.set(false);
        this.toast.success('Đã bắt đầu', 'Trạng thái: Đang làm');
        this.changed.emit();
        this.loadActivity(u.id);
      },
      error: (e) => { this.busyLifecycle.set(false); this.toast.error('Không đổi được trạng thái', e?.error?.message ?? ''); }
    });
  }
  openStart(t: ProjectTask): void {
    this.startEst.set(t.estimateHours ? String(t.estimateHours) : '');
    // Ngày bắt đầu trống → gợi ý hôm nay (bắt đầu làm = hôm nay).
    this.startFromIso.set(this.isoFromDmy(t.startDate) || this.todayIso());
    this.startDueIso.set(this.isoFromDmy(t.dueDate));
    this.pickStartOpen.set(true);
  }
  /** Hôm nay dạng yyyy-MM-dd (local) cho &lt;input type=date&gt;. */
  private todayIso(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
  cancelStart(): void { this.pickStartOpen.set(false); }
  /** Lưu est + ngày hoàn thành rồi chuyển Đang làm. */
  confirmStart(): void {
    const t = this.current();
    if (!t || this.busyLifecycle()) return;
    const est = Number(this.startEst());
    if (!(est > 0)) { this.toast.warning('Nhập Ước lượng (est) lớn hơn 0'); return; }
    if (!this.startFromIso()) { this.toast.warning('Chọn Ngày bắt đầu'); return; }
    if (!this.startDueIso()) { this.toast.warning('Chọn Ngày hoàn thành'); return; }
    if (this.startFromIso() > this.startDueIso()) { this.toast.warning('Ngày bắt đầu phải trước hoặc bằng Ngày hoàn thành'); return; }
    this.busyLifecycle.set(true);
    const body = this.buildRequest(t, {
      estimateHours: est,
      startDate: this.dmyFromIso(this.startFromIso()),
      dueDate: this.dmyFromIso(this.startDueIso())
    });
    this.svc.updateTask(this.projectId(), t.id, body).subscribe({
      next: (u) => { this.model.set(u); this.doStart(u); },
      error: (e) => { this.busyLifecycle.set(false); this.toast.error('Không lưu được est/hạn', e?.error?.message ?? ''); }
    });
  }
  /** dd/MM/yyyy → yyyy-MM-dd (đổ vào input date). */
  private isoFromDmy(d: string | null): string {
    if (!d) return '';
    const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(d);
    return m ? `${m[3]}-${m[2]}-${m[1]}` : '';
  }
  /** yyyy-MM-dd → dd/MM/yyyy (gửi lên TaskRequest). */
  private dmyFromIso(d: string): string | null {
    if (!d) return null;
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(d);
    return m ? `${m[3]}/${m[2]}/${m[1]}` : d;
  }

  /**
   * Chuyển Kiểm thử được không mà KHÔNG cần chọn tester?
   * - BUG/ISSUE → BE tự bàn giao cho người log → luôn được.
   * - Task thường đã có testerUserId → được.
   * Ngược lại (task thường chưa có tester) → phải chọn tester trước.
   */
  /** Việc không cần kiểm thử (PM/BA) → ẩn bước Kiểm thử, cho Hoàn thành thẳng từ Đang làm. */
  readonly skipTest = computed(() => !!this.current()?.skipTest && !this.isBug());
  /** Bật/tắt cờ "không cần kiểm thử" — backend chặn nếu task đã sang Kiểm thử trở đi. */
  toggleSkipTest(v: boolean): void {
    const t = this.current();
    if (!t) return;
    this.svc.updateTask(this.projectId(), t.id, this.buildRequest(t, { skipTest: v })).subscribe({
      next: (u) => {
        this.model.set(u);
        this.toast.success(v ? 'Đã đánh dấu không cần kiểm thử' : 'Đã bật lại yêu cầu kiểm thử');
        this.changed.emit();
      },
      error: (e) => this.toast.error('Không đổi được', e?.error?.message ?? '')
    });
  }

  readonly canReviewDirectly = computed(() => {
    const t = this.current();
    if (!t) return false;
    return this.isBug() || !!t.testerUserId;
  });

  /**
   * Bấm "→ Chuyển Kiểm thử": chọn tester (nếu thiếu) → NHẬP GIỜ lập trình → chuyển.
   * Giờ là bắt buộc (backend chặn) nên luôn phải qua popup.
   */
  toReview(): void {
    if (this.canReviewDirectly()) { this.askWork('DEV', (w) => this.doReview(w)); return; }
    this.openPickTester();
  }

  // ===== Ghi giờ HẰNG NGÀY (không chờ tới lúc bàn giao) =====
  readonly workLogs = signal<WorkLog[]>([]);
  readonly logOpen = signal(false);
  readonly logRole = signal<WorkRole>('DEV');
  readonly savingLog = signal(false);

  /** Tổng giờ đã ghi, tách theo vai — hiện ngay dưới nút. */
  readonly devHours = computed(() => this.sumHours('DEV'));
  readonly testHours = computed(() => this.sumHours('TEST'));
  private sumHours(role: WorkRole): number {
    const s = this.workLogs().filter((w) => w.role === role).reduce((a, w) => a + (w.hours || 0), 0);
    return Math.round(s * 100) / 100;
  }

  private loadWorkLogs(taskId: string): void {
    this.svc.listTaskWorkLogs(this.projectId(), taskId).subscribe({
      next: (w) => this.workLogs.set(w ?? []),
      error: () => this.workLogs.set([])
    });
  }
  /** Mở popup ghi giờ thủ công (vai do người dùng chọn qua nút bấm). */
  openLog(role: WorkRole): void {
    this.logRole.set(role);
    this.logOpen.set(true);
  }
  onLogConfirmed(w: WorkEntry): void {
    const t = this.current();
    if (!t || this.savingLog()) return;
    this.savingLog.set(true);
    this.svc.addWorkLog(this.projectId(), t.id, { ...w, role: this.logRole() }).subscribe({
      next: () => {
        this.savingLog.set(false);
        this.logOpen.set(false);
        this.toast.success('Đã ghi giờ', w.hours + 'h ngày ' + w.workDate);
        this.loadWorkLogs(t.id);
        this.loadActivity(t.id);
        this.changed.emit();
      },
      error: (e) => { this.savingLog.set(false); this.toast.error('Không ghi được giờ', e?.error?.message ?? ''); }
    });
  }
  /** Dòng giờ đang mở ô chọn người (null = không mở). */
  readonly reassigningLog = signal<string | null>(null);

  /**
   * Nắn lại NGƯỜI của một dòng giờ ghi nhầm.
   *
   * Đổi người thực hiện / kiểm thử của task KHÔNG kéo giờ cũ theo — mỗi dòng giờ thuộc về bước và
   * người đã làm bước đó, nên bàn giao thật thì công của người trước vẫn còn nguyên. Khi gán nhầm
   * người rồi mới sửa thì dùng chỗ này để nắn đúng dòng bị sai.
   */
  reassignLog(w: WorkLog, userId: string): void {
    this.reassigningLog.set(null);
    const t = this.task();
    if (!userId || userId === w.userId || !t) {
      return;
    }
    this.svc.reassignWorkLog(this.projectId(), w.id, userId).subscribe({
      next: () => {
        this.toast.success('Đã chuyển dòng giờ sang người khác');
        this.loadWorkLogs(t.id);
        this.changed.emit();
      },
      error: (e) => this.toast.error('Không chuyển được dòng giờ', e?.error?.message ?? '')
    });
  }

  deleteLog(w: WorkLog): void {
    const t = this.current();
    if (!t) return;
    this.svc.deleteWorkLog(this.projectId(), w.id).subscribe({
      next: () => { this.toast.success('Đã xoá dòng giờ'); this.loadWorkLogs(t.id); this.changed.emit(); },
      error: (e) => this.toast.error('Không xoá được', e?.error?.message ?? '')
    });
  }
  roleLabel(r: WorkRole): string { return r === 'DEV' ? 'Lập trình' : 'Kiểm thử'; }
  /** Hành động sinh ra dòng giờ — phân biệt log lỗi / bàn giao / duyệt xong / trả về sửa. */
  actLabel(w: WorkLog): string { return workActionLabel(w.action, w.role); }
  actColor(w: WorkLog): string { return workActionColor(w.action, w.role); }

  /** Tổng giờ THỰC TẾ đã ghi trên task (mọi vai, mọi lượt kể cả bị trả về làm lại). */
  readonly totalHours = computed(() => Math.round((this.devHours() + this.testHours()) * 100) / 100);
  /**
   * Độ lệch tổng giờ thực tế so với ƯỚC LƯỢNG của task.
   * Task bị trả về sửa lại nhiều lượt sẽ cộng dồn giờ nên rất dễ vượt est — đó chính là
   * tín hiệu cần nhìn, không phải lỗi số liệu.
   */
  readonly hoursVariance = computed(() => {
    const est = this.current()?.estimateHours ?? 0;
    if (!est || !this.totalHours()) return null;
    return Math.round(((this.totalHours() - est) / est) * 100);
  });

  // ===== Popup nhập giờ tại mốc bàn giao =====
  readonly workOpen = signal(false);
  readonly workRole = signal<WorkRole>('DEV');
  /** Lần chuyển này sẽ lấy "Ngày tính công" làm hạn hoàn thành (task đang chưa có hạn). */
  readonly workFillsDue = signal(false);
  private workDone: ((w: WorkEntry) => void) | null = null;

  /** Mở popup nhập giờ; xác nhận xong mới chạy tiếp hành động chuyển trạng thái. */
  private askWork(role: WorkRole, then: (w: WorkEntry) => void, fillsDue = false): void {
    this.workRole.set(role);
    this.workFillsDue.set(fillsDue && !this.current()?.dueDate);
    this.workDone = then;
    this.workOpen.set(true);
  }
  onWorkConfirmed(w: WorkEntry): void {
    this.workOpen.set(false);
    const fn = this.workDone;
    this.workDone = null;
    fn?.(w);
  }
  onWorkCancelled(): void {
    this.workOpen.set(false);
    this.workDone = null;
  }

  /** Mở/đóng khu chọn người kiểm thử (inline). Mặc định gợi ý tester hiện có. */
  openPickTester(): void {
    const t = this.current();
    this.testerUserId.set(t?.testerUserId || '');
    this.pickTesterOpen.set(true);
  }
  cancelPickTester(): void { this.pickTesterOpen.set(false); }

  /** Chuyển sang KIỂM THỬ (BE tự bàn giao PIC: task→tester, bug/issue→người log). */
  private doReview(work?: WorkEntry): void {
    const t = this.current();
    if (!t || this.busyLifecycle()) return;
    this.busyLifecycle.set(true);
    this.svc.updateTaskStatus(this.projectId(), t.id, 'IN_REVIEW', work).subscribe({
      next: (u) => {
        this.model.set(u);
        this.busyLifecycle.set(false);
        this.pickTesterOpen.set(false);
        const name = u.assigneeName || 'người kiểm thử';
        this.toast.success('Đã chuyển kiểm thử', `Cho ${name}`);
        this.changed.emit();
        this.loadActivity(u.id);
      },
      error: (e) => { this.busyLifecycle.set(false); this.toast.error('Không chuyển được trạng thái', e?.error?.message ?? ''); }
    });
  }

  /**
   * Task thường CHƯA có tester → chọn người rồi setTester(uid) RỒI updateTaskStatus(IN_REVIEW).
   */
  confirmToReview(): void {
    const t = this.current();
    if (!t || this.busyLifecycle()) return;
    const testerId = this.testerUserId() || null;
    if (!testerId) { this.toast.warning('Chọn người kiểm thử'); return; }
    this.askWork('DEV', (w) => this.doConfirmToReview(testerId, w));
  }

  /** Gán tester rồi chuyển Kiểm thử kèm giờ lập trình. */
  private doConfirmToReview(testerId: string, work: WorkEntry): void {
    const t = this.current();
    if (!t) return;
    this.busyLifecycle.set(true);
    const body = this.buildRequest(t, { testerUserId: testerId });
    this.svc.updateTask(this.projectId(), t.id, body).subscribe({
      next: (afterSet) => {
        this.model.set(afterSet);
        this.svc.updateTaskStatus(this.projectId(), t.id, 'IN_REVIEW', work).subscribe({
          next: (u) => {
            this.model.set(u);
            this.busyLifecycle.set(false);
            this.pickTesterOpen.set(false);
            const name = u.assigneeName || afterSet.testerName || 'người kiểm thử';
            this.toast.success('Đã chuyển kiểm thử', `Cho ${name}`);
            this.changed.emit();
            this.loadActivity(u.id);
          },
          error: (e) => { this.busyLifecycle.set(false); this.toast.error('Không chuyển được trạng thái', e?.error?.message ?? ''); }
        });
      },
      error: (e) => { this.busyLifecycle.set(false); this.toast.error('Không gán được người kiểm thử', e?.error?.message ?? ''); }
    });
  }

  /** IN_REVIEW → DONE: nhập giờ kiểm thử rồi hoàn thành. */
  completeTask(): void {
    this.askWork(this.skipTest() ? 'DEV' : 'TEST', (w) => this.doComplete(w), true);
  }
  private doComplete(work: WorkEntry): void {
    const t = this.current();
    if (!t || this.busyLifecycle()) return;
    this.busyLifecycle.set(true);
    this.svc.updateTaskStatus(this.projectId(), t.id, 'DONE', work).subscribe({
      next: (u) => {
        this.model.set(u);
        this.busyLifecycle.set(false);
        this.toast.success('Đã hoàn thành', u.code);
        this.changed.emit();
        this.loadActivity(u.id);
      },
      error: (e) => { this.busyLifecycle.set(false); this.toast.error('Không hoàn thành được', e?.error?.message ?? ''); }
    });
  }

  // ===== Reopen: Kiểm thử CHƯA đạt → trả về Đang làm (giao lại người thực hiện) =====
  openReopen(): void {
    this.reopenAssignee.set(this.current()?.assigneeUserId || '');
    this.pickReopenOpen.set(true);
  }
  cancelReopen(): void { this.pickReopenOpen.set(false); }
  confirmReopen(): void {
    // Trả về Đang làm = tester đã kiểm thử xong và kết luận chưa đạt → phải ghi công đó.
    this.askWork('TEST', (w) => this.doConfirmReopen(w));
  }
  private doConfirmReopen(work: WorkEntry): void {
    const t = this.current();
    if (!t || this.busyLifecycle()) return;
    // Ô người sửa để TRỐNG nghĩa là "giữ nguyên người thực hiện" (đúng như nhãn), KHÔNG phải
    // gỡ người. Trước đây lấy thẳng giá trị rỗng rồi gọi assignTask(null) — reopen xong task
    // rơi vào Đang làm mà không còn ai phụ trách, biến mất khỏi việc của mọi người.
    const target = this.reopenAssignee() || t.assigneeUserId || null;
    if (!target) {
      this.toast.error('Chưa có người sửa', 'Task chưa gán người thực hiện — chọn người sửa trước khi Reopen.');
      return;
    }
    this.busyLifecycle.set(true);
    const toProgress = () => this.svc.updateTaskStatus(this.projectId(), t.id, 'IN_PROGRESS', work).subscribe({
      next: (u) => {
        this.model.set(u);
        this.busyLifecycle.set(false);
        this.pickReopenOpen.set(false);
        this.toast.success('Đã Reopen', `Trả về Đang làm${u.assigneeName ? ' · ' + u.assigneeName : ''}`);
        this.changed.emit();
        this.loadActivity(u.id);
      },
      error: (e) => { this.busyLifecycle.set(false); this.toast.error('Không Reopen được', e?.error?.message ?? ''); }
    });
    // Giao lại người thực hiện nếu đổi, rồi mới chuyển trạng thái.
    if (target !== (t.assigneeUserId || null)) {
      this.svc.assignTask(this.projectId(), t.id, target).subscribe({
        next: (u) => { this.model.set(u); toProgress(); },
        error: (e) => { this.busyLifecycle.set(false); this.toast.error('Không giao được người thực hiện', e?.error?.message ?? ''); }
      });
    } else {
      toProgress();
    }
  }

  // ===== Chi tiết lỗi (BUG/ISSUE) — sửa inline =====
  startEditBug(): void {
    const t = this.current();
    if (!t) return;
    this.bugForm = {
      severity: t.severity ?? null,
      stepsToReproduce: t.stepsToReproduce ?? '',
      expectedResult: t.expectedResult ?? '',
      actualResult: t.actualResult ?? '',
      environment: t.environment ?? ''
    };
    this.editingBug.set(true);
  }
  cancelEditBug(): void { this.editingBug.set(false); }

  /** Mô tả hiển thị: gộp Bước/Kết quả cũ (nếu bug cũ còn tách trường) để không mất dữ liệu. */
  bugDesc(t: ProjectTask): string { return mergeBugFieldsIntoDescription(t); }

  saveBug(): void {
    const t = this.current();
    if (!t || this.busyBug()) return;
    this.busyBug.set(true);
    // PUT đầy đủ — giữ nguyên các trường hiện có của task, chỉ đổi 5 trường chi tiết lỗi.
    const body: TaskRequest = {
      parentId: t.parentId, title: t.title, description: t.description,
      type: t.type, status: t.status, priority: t.priority,
      assigneeUserId: t.assigneeUserId, estimateHours: t.estimateHours,
      startDate: t.startDate, dueDate: t.dueDate, orderIndex: t.orderIndex, screen: t.screen,
      severity: this.bugForm.severity,
      // Bước/Kết quả đã gộp vào Mô tả — giữ nguyên dữ liệu cũ (không sửa ở đây nữa) để không mất.
      stepsToReproduce: t.stepsToReproduce,
      expectedResult: t.expectedResult,
      actualResult: t.actualResult,
      environment: this.bugForm.environment.trim() || null
    };
    this.svc.updateTask(this.projectId(), t.id, body).subscribe({
      next: (u) => {
        this.model.set(u);
        this.busyBug.set(false);
        this.editingBug.set(false);
        this.toast.success('Đã cập nhật chi tiết lỗi', u.code);
        this.changed.emit();
        this.loadActivity(u.id);
      },
      error: (e) => { this.busyBug.set(false); this.toast.error('Không cập nhật được', e?.error?.message ?? ''); }
    });
  }

  /** Badge mức độ: Blocker/Critical = đỏ; Major = cam; Minor/Trivial = xám. */
  severityBadge(s: BugSeverity | null): string {
    switch (s) {
      case 'BLOCKER':
      case 'CRITICAL': return 'sev sev--red';
      case 'MAJOR': return 'sev sev--orange';
      default: return 'sev sev--gray';
    }
  }

  setTab(t: SubTab): void { this.tab.set(t); }

  // ===== Bình luận =====
  /**
   * Gửi bình luận. Có ảnh chờ → TẠO bình luận trước để lấy id, RỒI upload từng ảnh kèm commentId
   * (ảnh chỉ tồn tại khi bình luận tồn tại — cùng lối với form Tạo nhanh).
   */
  submitComment(): void {
    const t = this.current();
    const body = this.draft().trim();
    const imgs = this.cmtImages();
    if (!t || (!body && !imgs.length)) return;
    if (this.sendingComment()) return;
    this.sendingComment.set(true);
    this.svc.addComment(this.projectId(), t.id, body || '(ảnh)').subscribe({
      next: (c) => {
        this.comments.update((cs) => [...cs, c]);
        this.draft.set('');
        if (!imgs.length) { this.finishComment(t.id); return; }
        const ups = imgs.map((p) =>
          this.svc.uploadAttachment(this.projectId(), t.id, p.file, c.id).pipe(catchError(() => of(null))));
        forkJoin(ups).subscribe((res) => {
          const ok = res.filter((x): x is TaskAttachment => x !== null);
          const failed = res.length - ok.length;
          this.attachments.update((xs) => [...xs, ...ok]);
          if (failed) this.toast.warning(`Đã gửi bình luận; ${failed}/${res.length} ảnh tải lỗi`);
          this.clearCmtImages();
          this.finishComment(t.id);
        });
      },
      error: () => { this.sendingComment.set(false); this.toast.error('Không gửi được bình luận'); }
    });
  }
  private finishComment(taskId: string): void {
    this.sendingComment.set(false);
    this.loadActivity(taskId);
  }

  /** Ảnh của một bình luận (đã upload) — lấy từ danh sách đính kèm theo commentId. */
  commentImages(c: TaskComment): TaskAttachment[] {
    return this.attachments().filter((a) => a.commentId === c.id);
  }

  // ===== Ảnh chờ trong ô soạn bình luận =====
  /** Thêm ảnh vào hàng chờ của ô bình luận (dán hoặc chọn file) — upload sau khi bấm Gửi. */
  private addCmtImages(files: File[]): void {
    if (!files.length) return;
    this.cmtImages.update((xs) => [...xs, ...files.map((file) => ({ file, url: URL.createObjectURL(file) }))]);
  }
  onCmtFilesPicked(e: Event): void {
    const input = e.target as HTMLInputElement;
    this.addCmtImages(Array.from(input.files ?? []).filter((f) => f.type.startsWith('image/')));
    input.value = ''; // cho phép chọn lại cùng file
  }
  removeCmtImage(i: number): void {
    const list = this.cmtImages();
    if (list[i]) URL.revokeObjectURL(list[i].url);
    this.cmtImages.set(list.filter((_, idx) => idx !== i));
  }
  private clearCmtImages(): void {
    for (const p of this.cmtImages()) URL.revokeObjectURL(p.url);
    this.cmtImages.set([]);
  }
  /**
   * Enter = XUỐNG DÒNG, gửi bằng Ctrl/Cmd+Enter.
   *
   * Trước đây Enter gửi luôn: bình luận mô tả lỗi thường phải xuống dòng, gõ nửa chừng bấm
   * Enter là gửi mất một mẩu dở dang, sửa lại thì bình luận dính nhãn "(đã sửa)". Ô SỬA
   * bình luận lại để Enter xuống dòng bình thường — cùng một khung soạn thảo mà hai hành vi
   * khác nhau, gõ theo thói quen là sai.
   */
  onCommentKey(e: KeyboardEvent): void {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); this.submitComment(); }
  }

  /** Ô sửa bình luận: cùng quy ước — Enter xuống dòng, Ctrl/Cmd+Enter lưu. */
  onEditKey(e: KeyboardEvent, c: TaskComment): void {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); this.saveEdit(c); }
  }
  startEdit(c: TaskComment): void { this.editingId.set(c.id); this.editDraft.set(c.body); }
  cancelEdit(): void { this.editingId.set(null); this.editDraft.set(''); }
  saveEdit(c: TaskComment): void {
    const t = this.current();
    const body = this.editDraft().trim();
    if (!t || !body) return;
    this.svc.editComment(this.projectId(), t.id, c.id, body).subscribe({
      next: (u) => { this.comments.update((cs) => cs.map((x) => (x.id === c.id ? u : x))); this.cancelEdit(); },
      error: (e) => this.toast.error('Không sửa được', e?.error?.message ?? '')
    });
  }
  deleteComment(c: TaskComment): void {
    const t = this.current();
    if (!t || !window.confirm('Xoá bình luận này?')) return;
    this.svc.deleteComment(this.projectId(), t.id, c.id).subscribe({
      next: () => this.comments.update((cs) => cs.filter((x) => x.id !== c.id)),
      error: (e) => this.toast.error('Không xoá được', e?.error?.message ?? '')
    });
  }

  // ===== Ảnh đính kèm =====
  attUrl(a: TaskAttachment): string {
    const t = this.current();
    return t ? this.svc.attachmentUrl(this.projectId(), t.id, a.id) : a.url;
  }
  onUpload(e: Event): void {
    const t = this.current();
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!t || !file) return;
    this.svc.uploadAttachment(this.projectId(), t.id, file).subscribe({
      next: (a) => { this.attachments.update((xs) => [...xs, a]); input.value = ''; this.toast.success('Đã tải ảnh lên'); this.loadActivity(t.id); },
      error: (err) => { input.value = ''; this.toast.error('Không tải được ảnh', err?.error?.message ?? ''); }
    });
  }
  /**
   * Dán ảnh (Ctrl/Cmd+V) khi đang mở chi tiết task.
   * - Con trỏ đang ở Ô SOẠN BÌNH LUẬN → xếp vào hàng chờ của bình luận đó (gửi cùng bình luận).
   * - Ngược lại → upload ngay thành ảnh đính kèm chung của task (hành vi cũ).
   * Trước đây mọi thao tác dán đều rơi vào nhánh 2, nên dán trong ô bình luận thì ảnh
   * "biến mất" sang tab Chi tiết — đúng lỗi người dùng gặp.
   */
  @HostListener('document:paste', ['$event'])
  onPasteUpload(ev: ClipboardEvent): void {
    if (!this.open()) return;
    const t = this.current();
    if (!t) return;
    const items = ev.clipboardData?.items;
    if (!items) return;
    const files: File[] = [];
    for (const it of Array.from(items)) {
      if (it.kind === 'file' && it.type.startsWith('image/')) {
        const raw = it.getAsFile();
        if (!raw) continue;
        const ext = (it.type.split('/')[1] || 'png').replace('jpeg', 'jpg');
        files.push(raw.name && raw.name !== 'image.png'
          ? raw
          : new File([raw], `screenshot-${Date.now()}.${ext}`, { type: it.type }));
      }
    }
    if (!files.length) return;
    ev.preventDefault();
    if (this.pastingIntoComment(ev)) {
      this.addCmtImages(files);
      this.toast.success('Đã dán ảnh vào bình luận', 'Bấm Gửi để đăng');
      return;
    }
    for (const file of files) {
      this.svc.uploadAttachment(this.projectId(), t.id, file).subscribe({
        next: (a) => { this.attachments.update((xs) => [...xs, a]); this.toast.success('Đã dán & tải ảnh lên'); this.loadActivity(t.id); },
        error: (err) => this.toast.error('Không tải được ảnh dán', err?.error?.message ?? '')
      });
    }
  }

  /** Đích của thao tác dán có nằm trong ô soạn bình luận không (tab Bình luận + focus textarea)? */
  private pastingIntoComment(ev: ClipboardEvent): boolean {
    if (this.tab() !== 'comments') return false;
    const el = ev.target as HTMLElement | null;
    return !!el?.closest?.('.td__newcmt');
  }

  deleteAttachment(a: TaskAttachment): void {
    const t = this.current();
    if (!t || !window.confirm('Xoá ảnh này?')) return;
    this.svc.deleteAttachment(this.projectId(), t.id, a.id).subscribe({
      next: () => this.attachments.update((xs) => xs.filter((x) => x.id !== a.id)),
      error: (e) => this.toast.error('Không xoá được ảnh', e?.error?.message ?? '')
    });
  }

  // ===== Lightbox =====
  openLightbox(i: number): void { this.lightboxIndex.set(i); }
  closeLightbox(): void { this.lightboxIndex.set(null); }
  prevImg(): void {
    const n = this.attachments().length;
    this.lightboxIndex.update((i) => (i === null ? null : (i - 1 + n) % n));
  }
  nextImg(): void {
    const n = this.attachments().length;
    this.lightboxIndex.update((i) => (i === null ? null : (i + 1) % n));
  }

  // ===== Helpers =====
  typeBadge(t: TaskType): string {
    switch (t) {
      case 'BUG': return 'badge--cancel';
      case 'ISSUE': return 'badge--pending';
      case 'EPIC': return 'badge--active';
      default: return 'badge--neutral';
    }
  }
  priorityBadge(p: TaskPriority): string {
    switch (p) {
      case 'URGENT': return 'badge--cancel';
      case 'HIGH': return 'badge--pending';
      default: return 'badge--neutral';
    }
  }
  fmt(iso: string | null): string {
    if (!iso) return '';
    return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }
}
