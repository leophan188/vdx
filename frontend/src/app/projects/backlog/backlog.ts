import { Component, HostListener, OnInit, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Modal } from '../../shared/modal/modal';
import { SearchableSelect, SelectOption } from '../../shared/searchable-select/searchable-select';
import { buildParentOptions } from '../work-stats';
import { ToastService } from '../../shared/toast/toast.service';
import {
  ProjectService, ProjectTask, TaskRequest, TaskType, TaskStatus, TaskPriority, BugSeverity, ProjectMember, ReorderItem
} from '../../core/project.service';
import { memberPersonOptions } from '../../shared/person-options';
import { buildTree, hasChildren, subtreeLeafEstimate, effectiveStart, effectiveDue } from '../../shared/task-tree';
import { loadPref, savePref } from '../../shared/view-prefs';
import { TypeFilter } from '../../shared/type-filter/type-filter';
import { BUG_DESCRIPTION_TEMPLATE, mergeBugFieldsIntoDescription } from '../../shared/bug-template';

/** Một dòng cây (task phẳng + cấp thụt lề, giữ thứ tự DFS). */
interface TreeRow {
  task: ProjectTask;
  level: number;
  hasChildren: boolean;
}

/**
 * TAB Backlog — cây đa cấp cha-con của task trong dự án.
 * Dựng cây từ listTasks phẳng theo parentId, thụt lề theo cấp, gập/mở.
 * Thao tác mỗi dòng: thêm con, sửa, xoá (chỉ khi không con), đổi status, gán người.
 */
@Component({
  selector: 'app-prj-backlog',
  imports: [FormsModule, Modal, SearchableSelect, TypeFilter],
  templateUrl: './backlog.html',
  styles: [`
    .bl-summary { display: flex; gap: var(--space-2); flex-wrap: wrap; align-items: center; margin-bottom: var(--space-3); }
    /* Thanh lọc dùng class chuẩn .filter-bar (ở _components.scss). */
    .bl-toolbar__spacer { flex: 1; } /* còn dùng trong footer modal */
    .bl-chip { padding: 3px var(--space-2); border: 1px solid var(--color-border); border-radius: var(--radius-full);
      background: var(--color-surface); color: var(--color-text-muted); cursor: pointer; font-size: var(--font-size-xs); }
    .bl-chip:hover { border-color: var(--color-primary); color: var(--color-primary); }
    .bl-chip--on { background: var(--color-primary-soft); border-color: var(--color-primary);
      color: var(--color-primary); font-weight: 600; }
    .bl-tree { border: 1px solid var(--color-border); border-radius: var(--radius-md); overflow-x: auto; background: var(--color-surface); }
    .bl-head, .bl-row {
      display: grid;
      grid-template-columns: minmax(220px, 1.6fr) 92px 118px 150px 62px 110px 148px 148px 84px 96px 116px;
      align-items: center; gap: var(--space-2);
      padding: var(--space-2) var(--space-3); min-width: 1284px;
    }
    .bl-head { font-weight: 600; font-size: var(--font-size-sm); color: var(--color-text-muted);
      background: var(--color-surface-alt); border-bottom: 1px solid var(--color-border); }
    .bl-row { border-bottom: 1px solid var(--color-border); border-left: 4px solid transparent; }
    .bl-row:last-child { border-bottom: 0; }
    .bl-row:hover { background: var(--color-surface-alt); }
    /* Nhận diện theo loại (token có dark riêng): EPIC (tím) · Story (xanh dương) · Task cha (xanh ngọc). */
    .bl-row--epic { border-left-color: var(--type-epic); background: var(--type-epic-bg); }
    .bl-row--epic .bl-title { font-weight: 700; color: var(--type-epic); }
    .bl-row--story { border-left-color: var(--type-story); background: var(--type-story-bg); }
    .bl-row--story .bl-title { font-weight: 600; }
    .bl-row--parent { border-left-color: var(--type-parent); background: var(--type-parent-bg); }
    .bl-search { height: var(--control-h-sm); min-width: 220px; padding: 0 var(--space-3); border: 1px solid var(--color-border);
      border-radius: var(--radius-md); background: var(--color-surface); color: var(--color-text); font-size: var(--text-sm); }
    .bl-date { height: var(--control-h-sm); padding: 0 var(--space-2); border: 1px solid var(--color-border);
      border-radius: var(--radius-md); background: var(--color-surface); color: var(--color-text); font-size: var(--text-xs); }
    .bl-chips { display: inline-flex; gap: var(--space-1); flex-wrap: wrap; }
    .bl-chip { height: var(--control-h-sm); padding: 0 var(--space-3); border: 1px solid var(--color-border);
      border-radius: var(--radius-pill, 999px); background: var(--color-surface); color: var(--color-text-muted);
      font: inherit; font-size: var(--text-xs); cursor: pointer; white-space: nowrap; }
    .bl-chip:hover { border-color: var(--color-primary); color: var(--color-primary); }
    .bl-chip.is-on { background: var(--color-primary); border-color: var(--color-primary); color: var(--color-text-invert); font-weight: var(--weight-medium); }
    .bl-atts { display: flex; flex-wrap: wrap; gap: 8px; }
    .bl-att { position: relative; width: 72px; height: 72px; border-radius: 8px; overflow: hidden; border: 1px solid var(--color-border); }
    .bl-att img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .bl-att-del { position: absolute; top: 2px; right: 2px; width: 20px; height: 20px; padding: 0; border: none;
      border-radius: 50%; background: rgba(0,0,0,.6); color: #fff; font-size: .7rem; line-height: 20px; cursor: pointer;
      display: flex; align-items: center; justify-content: center; }
    .bl-att-add { width: 72px; height: 72px; border: 1px dashed var(--color-border); border-radius: 8px; display: flex;
      align-items: center; justify-content: center; font-size: 1.5rem; color: var(--color-text-muted); cursor: pointer; background: var(--color-surface); }
    .bl-att-add:hover { border-color: var(--color-primary); color: var(--color-primary); }
    .bl-att-hint { font-size: var(--text-xs); color: var(--color-text-muted); margin-top: 4px; }
    .bl-legend { display: inline-flex; gap: 12px; align-items: center; margin-left: auto; font-size: var(--text-xs); color: var(--color-text-muted); }
    .bl-legend__item { display: inline-flex; align-items: center; gap: 4px; }
    .bl-legend__dot { width: 12px; height: 12px; border-radius: 3px; border-left: 4px solid; }
    .bl-legend__dot--epic { border-left-color: var(--type-epic); background: var(--type-epic-bg); }
    .bl-legend__dot--story { border-left-color: var(--type-story); background: var(--type-story-bg); }
    .bl-legend__dot--parent { border-left-color: var(--type-parent); background: var(--type-parent-bg); }
    .bl-row--dragging { opacity: .45; }
    .bl-row--dragover { box-shadow: inset 0 2px 0 0 var(--color-primary); background: color-mix(in srgb, var(--color-primary) 8%, transparent); }
    .bl-drag { cursor: grab; color: var(--color-text-muted); font-size: .8rem; padding: 0 2px; user-select: none; flex: none; }
    .bl-drag:active { cursor: grabbing; }
    .bl-title { display: flex; align-items: center; gap: 4px; min-width: 0; }
    /* Nút thu gọn/mở to & dễ bấm hơn (vùng chạm 28px, có nền hover). */
    .bl-toggle { background: none; border: 0; cursor: pointer; width: 28px; height: 28px; font-size: 1.05rem;
      line-height: 1; color: var(--color-text-muted); padding: 0; flex: 0 0 auto; border-radius: var(--radius-sm, 6px);
      display: inline-flex; align-items: center; justify-content: center; }
    .bl-toggle:hover { background: var(--color-surface-alt, rgba(127,127,127,.12)); color: var(--color-primary); }
    .bl-leaf { display: inline-block; width: 28px; text-align: center; color: var(--color-border); flex: 0 0 auto; }
    .bl-code { font-family: var(--font-mono, monospace); font-size: var(--font-size-xs); color: var(--color-text-muted);
      flex: 0 0 auto; margin-right: 2px; }
    .bl-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
    .bl-name:hover { color: var(--color-primary); text-decoration: underline; }
    .bl-actions { display: flex; gap: 2px; justify-content: flex-end; }
    .bl-empty { padding: var(--space-4); text-align: center; color: var(--color-text-muted); }
    .bl-num { text-align: right; font-variant-numeric: tabular-nums; }
    .bl-num--muted { color: var(--color-text-muted); }
    .bl-date { font-size: var(--text-sm); font-variant-numeric: tabular-nums; white-space: nowrap; }
    /* Nhập nhanh est tại chỗ trên lưới (task lá) */
    .bl-est {
      width: 100%; max-width: 70px; text-align: right; font-variant-numeric: tabular-nums;
      padding: 2px var(--space-1); border: 1px solid var(--color-border); border-radius: var(--radius-sm);
      background: var(--color-surface); color: var(--color-text); font-size: var(--font-size-sm);
    }
    .bl-est:hover { border-color: var(--color-primary-soft); }
    .bl-est:focus { outline: none; border-color: var(--color-primary);
      box-shadow: 0 0 0 2px var(--color-primary-soft); }
    /* Chọn/sửa ngày ngay trên lưới (task lá) */
    .bl-date-input {
      width: 100%; max-width: 140px; font-variant-numeric: tabular-nums;
      padding: 2px var(--space-1); border: 1px solid var(--color-border); border-radius: var(--radius-sm);
      background: var(--color-surface); color: var(--color-text); font-size: var(--font-size-sm);
    }
    .bl-date-input:hover { border-color: var(--color-primary-soft); }
    .bl-date-input:focus { outline: none; border-color: var(--color-primary);
      box-shadow: 0 0 0 2px var(--color-primary-soft); }
    .field-hint { font-size: var(--font-size-xs); color: var(--color-text-muted); margin-top: 2px; }
    .bl-form { display: grid; gap: var(--space-3); width: 100%; }
    /* Chọn loại dạng pill (đồng bộ với quick-create) */
    .bl-seg { display: flex; gap: 6px; flex-wrap: wrap; }
    .bl-seg button { border: 1px solid var(--color-border); background: var(--color-surface);
      padding: 6px 14px; border-radius: 999px; font-size: .85rem; cursor: pointer; color: var(--color-text); }
    .bl-seg button.is-active { background: var(--color-primary); border-color: var(--color-primary); color: var(--color-text-invert); font-weight: 600; }
    .bl-keep { display: inline-flex; align-items: center; gap: var(--space-2);
      font-size: var(--font-size-sm); color: var(--color-text-muted); cursor: pointer; user-select: none; }
    .bl-keep input { width: 16px; height: 16px; cursor: pointer; }

    /* Thanh tiến độ % hoàn thành */
    .bl-progress { display: flex; align-items: center; gap: var(--space-2); min-width: 0; }
    .bl-progress__track {
      flex: 1; min-width: 40px; height: 8px; border-radius: 999px;
      background: var(--color-surface-alt); border: 1px solid var(--color-border); overflow: hidden;
    }
    .bl-progress__bar { height: 100%; border-radius: 999px; background: var(--color-primary); transition: width .2s ease; }
    .bl-progress__bar--done { background: var(--color-success, var(--color-primary)); }
    .bl-progress__label { font-size: var(--font-size-xs); color: var(--color-text-muted);
      font-variant-numeric: tabular-nums; flex: 0 0 auto; width: 34px; text-align: right; }

    /* Thanh tiến độ chung trong khu tổng */
    .bl-summary__overall { display: flex; align-items: center; gap: var(--space-2); min-width: 180px; flex: 1; max-width: 280px; }
    .bl-summary__track { flex: 1; height: 10px; border-radius: 999px;
      background: var(--color-surface-alt); border: 1px solid var(--color-border); overflow: hidden; }
    .bl-summary__bar { height: 100%; border-radius: 999px; background: var(--color-primary); transition: width .2s ease; }
  `]
})
export class PrjBacklog implements OnInit {
  private svc = inject(ProjectService);
  private toast = inject(ToastService);

  readonly projectId = input.required<string>();

  /** "Backlog của tôi": userId để MẶC ĐỊNH lọc theo người này (vẫn hiện task cha). Rỗng → dùng pref chung của dự án. */
  readonly presetAssignee = input<string>('');

  /** Phát khi bấm tiêu đề một task → cha mở chi tiết task. */
  readonly openTask = output<ProjectTask>();

  readonly tasks = signal<ProjectTask[]>([]);
  readonly loading = signal(true);
  /** CHỈ thành viên dự án (không lấy toàn bộ nhân sự hệ thống). */
  readonly members = signal<ProjectMember[]>([]);
  readonly collapsed = signal<Set<string>>(new Set());

  // ----- Modal tạo/sửa -----
  readonly modalOpen = signal(false);
  readonly editingId = signal<string | null>(null);
  /** Ảnh chờ đính kèm — upload sau khi tạo/lưu task (cần id task). */
  readonly previews = signal<{ file: File; url: string; name: string }[]>([]);

  /** Thu hồi blob URL + xoá danh sách ảnh chờ. */
  private clearImages(): void {
    for (const p of this.previews()) { URL.revokeObjectURL(p.url); }
    this.previews.set([]);
  }
  /** Chọn thêm ảnh (nhiều) — chỉ image/*, dựng blob URL xem trước. */
  onFilesPicked(e: Event): void {
    const input = e.target as HTMLInputElement;
    const picked = Array.from(input.files ?? []).filter((f) => f.type.startsWith('image/'));
    if (picked.length) {
      this.previews.update((xs) => [...xs, ...picked.map((file) => ({ file, url: URL.createObjectURL(file), name: file.name }))]);
    }
    input.value = '';
  }
  /** Dán ảnh (Ctrl/Cmd+V) khi modal thêm/sửa đang mở → thêm vào hàng chờ ảnh. */
  @HostListener('document:paste', ['$event'])
  onPaste(ev: ClipboardEvent): void {
    if (!this.modalOpen()) return;
    const items = ev.clipboardData?.items;
    if (!items) return;
    const added: { file: File; url: string; name: string }[] = [];
    for (const it of Array.from(items)) {
      if (it.kind !== 'file' || !it.type.startsWith('image/')) continue;
      const raw = it.getAsFile();
      if (!raw) continue;
      let file = raw;
      if (!raw.name || raw.name === 'image.png') {
        const ext = (raw.type.split('/')[1] || 'png').replace('jpeg', 'jpg');
        file = new File([raw], `screenshot-${Date.now()}.${ext}`, { type: raw.type });
      }
      added.push({ file, url: URL.createObjectURL(file), name: file.name });
    }
    if (!added.length) return;
    this.previews.update((xs) => [...xs, ...added]);
    ev.preventDefault();
    this.toast.success('Đã dán ảnh', `${added.length} ảnh vào hàng chờ`);
  }
  /** Bỏ 1 ảnh chờ. */
  removeFile(i: number): void {
    const p = this.previews()[i];
    if (p) { URL.revokeObjectURL(p.url); }
    this.previews.set(this.previews().filter((_, idx) => idx !== i));
  }
  readonly saving = signal(false);
  f: TaskRequest = this.emptyForm();
  // ngày dạng yyyy-MM-dd cho <input type=date>; convert khi gửi.
  startIso = '';
  dueIso = '';
  /** Nhập nhanh: tích chọn → sau khi Lưu KHÔNG đóng popup, giữ form để thêm task tiếp. */
  keepAdding = false;

  readonly typeOptions: { value: TaskType; label: string }[] = [
    { value: 'EPIC', label: 'Epic' }, { value: 'STORY', label: 'Story' },
    { value: 'TASK', label: 'Task' }, { value: 'SUBTASK', label: 'Sub-task' },
    { value: 'BUG', label: 'Bug' }, { value: 'ISSUE', label: 'Issue' }
  ];
  readonly statusOptions: { value: TaskStatus; label: string }[] = [
    { value: 'BACKLOG', label: 'Backlog' }, { value: 'TODO', label: 'Cần làm' },
    { value: 'IN_PROGRESS', label: 'Đang làm' }, { value: 'IN_REVIEW', label: 'Kiểm thử' },
    { value: 'DONE', label: 'Hoàn thành' }, { value: 'CANCELLED', label: 'Huỷ' }
  ];
  readonly priorityOptions: { value: TaskPriority; label: string }[] = [
    { value: 'LOW', label: 'Thấp' }, { value: 'MEDIUM', label: 'Trung bình' },
    { value: 'HIGH', label: 'Cao' }, { value: 'URGENT', label: 'Khẩn cấp' }
  ];

  /** Mức độ nghiêm trọng (BUG/ISSUE) — đồng bộ với task-detail. */
  readonly severityOptions: { value: BugSeverity; label: string }[] = [
    { value: 'BLOCKER', label: 'Chặn' }, { value: 'CRITICAL', label: 'Nguy kịch' },
    { value: 'MAJOR', label: 'Lớn' }, { value: 'MINOR', label: 'Nhỏ' },
    { value: 'TRIVIAL', label: 'Không đáng kể' }
  ];

  readonly typeSel: SelectOption[] = this.typeOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly statusSel: SelectOption[] = this.statusOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly prioritySel: SelectOption[] = this.priorityOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly severitySel: SelectOption[] = this.severityOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly peopleSel = computed<SelectOption[]>(() => memberPersonOptions(this.members()));

  // ===== Phân cấp cha-con theo LOẠI =====
  /** Loại cha HỢP LỆ cho một loại con (null = không có cha — chỉ EPIC). */
  private parentTypeOf(type: TaskType | null | undefined): TaskType[] | null {
    switch (type) {
      case 'EPIC': return null;               // gốc, không có cha
      case 'STORY': return ['EPIC'];
      case 'TASK': return ['STORY', 'EPIC'];   // Task gắn dưới Story HOẶC thẳng lên Epic
      case 'SUBTASK': return ['TASK'];
      case 'BUG':
      case 'ISSUE': return ['TASK', 'SUBTASK'];
      default: return null;
    }
  }
  /** Loại CON hợp lệ đầu tiên khi bấm "+" trên 1 dòng theo loại cha. */
  private firstChildTypeOf(parentType: TaskType | null | undefined): TaskType {
    switch (parentType) {
      case 'EPIC': return 'STORY';
      case 'STORY': return 'TASK';
      case 'TASK': return 'SUBTASK';
      case 'SUBTASK': return 'BUG';
      default: return 'TASK';
    }
  }
  /** Nhãn gọn của loại cha yêu cầu (để hiển thị toast/hint). */
  parentTypeLabel(type: TaskType | null | undefined): string {
    const pt = this.parentTypeOf(type);
    if (!pt) return '';
    return pt.map((t) => this.typeLabel(t)).join(' hoặc ');
  }
  /** Loại con đang chọn CÓ cần cha không (khác EPIC). Method thường để bám theo f.type. */
  needsParent(): boolean { return this.parentTypeOf(this.f.type) !== null; }
  /** Danh sách cha hợp lệ theo f.type (loại các task không đúng loại cha yêu cầu). */
  parentOptions(): SelectOption[] {
    const allow = this.parentTypeOf(this.f.type);
    if (!allow) return [];
    const editing = this.editingId();
    return buildParentOptions(this.tasks(),
      this.tasks().filter((t) => allow.includes(t.type) && t.id !== editing));
  }
  /** Khi đổi loại trong modal → tính lại cha hợp lệ; reset parentId nếu không còn hợp lệ. */
  onTypeChange(type: TaskType): void {
    this.f.type = type;
    // Tạo mới + đổi sang BUG/ISSUE mà Mô tả còn trống → điền sẵn khung Mô tả lỗi.
    if (!this.editingId() && this.isBugLike(type) && !(this.f.description || '').trim()) {
      this.f.description = BUG_DESCRIPTION_TEMPLATE;
    }
    const allow = this.parentTypeOf(type);
    if (!allow) { this.f.parentId = null; return; }        // EPIC → không cha
    const ok = this.tasks().some((t) => t.id === this.f.parentId && allow.includes(t.type));
    if (!ok) this.f.parentId = null;                        // cha cũ không hợp loại mới → bỏ
  }

  /** Cây task (byId + childrenOf) — dùng cho rollup est cha / phát hiện có con. */
  readonly tree = computed(() => buildTree(this.tasks()));

  /** Lọc theo LOẠI (Epic/Story/Task/Sub-task/Bug/Issue) — lưu localStorage THEO DỰ ÁN cho lần sau. */
  readonly allTypes: TaskType[] = ['EPIC', 'STORY', 'TASK', 'SUBTASK', 'BUG', 'ISSUE'];
  readonly typeFilter = signal<Set<TaskType>>(new Set(this.allTypes));
  /** Lọc theo TRẠNG THÁI — bật hết = không lọc. Giữ cả task cha của task khớp. */
  readonly allStatuses: TaskStatus[] = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE', 'CANCELLED'];
  readonly statusFilter = signal<Set<TaskStatus>>(new Set(this.allStatuses));
  /** Lọc theo NGƯỜI THỰC HIỆN (rỗng = tất cả; '__UNASSIGNED__' = chưa gán) — lưu theo dự án. */
  readonly filterAssignee = signal('');
  /** Đang xuất Excel backlog. */
  readonly exporting = signal(false);
  exportExcel(): void {
    if (this.exporting()) return;
    this.exporting.set(true);
    // Xuất ĐÚNG theo bộ lọc đang áp (loại/trạng thái/người/tìm) — gửi danh sách task đang khớp lọc.
    const ids = this.filteredRows().map((r) => r.task.id);
    this.svc.exportBacklog(this.projectId(), ids).subscribe({
      next: (b) => { ProjectService.downloadBlob(b, 'backlog.xlsx'); this.exporting.set(false); },
      error: () => { this.exporting.set(false); this.toast.error('Không xuất được Excel backlog'); }
    });
  }
  /** Tìm theo từ khoá (tên/mã task) — không lưu, giữ cả cấp cha khi khớp. */
  readonly search = signal('');
  /** Giá trị đặc biệt cho lọc "Chưa gán". */
  readonly UNASSIGNED = '__UNASSIGNED__';

  /** Lọc theo KHOẢNG NGÀY (yyyy-MM-dd) — task có lịch [bắt đầu, kết thúc] CHẠM khoảng này (không lưu). */
  readonly dateFrom = signal('');
  readonly dateTo = signal('');
  /** Chips lọc NHANH (AND, chỉ áp task LÁ): 'noassignee' | 'nodeadline' | 'overdue'. */
  readonly quickFlags = signal<Set<string>>(new Set());
  isFlagOn(f: string): boolean { return this.quickFlags().has(f); }
  toggleFlag(f: string): void {
    const s = new Set(this.quickFlags());
    s.has(f) ? s.delete(f) : s.add(f);
    this.quickFlags.set(s);
  }
  /** Xoá bộ lọc phụ (tìm/người/ngày/chips) — giữ lọc Loại & Trạng thái. */
  clearQuickFilters(): void {
    this.search.set('');
    this.dateFrom.set('');
    this.dateTo.set('');
    this.quickFlags.set(new Set());
    this.setAssignee('');
  }
  /** Parse dd/MM/yyyy → mốc thời gian (ms); null nếu rỗng/sai. */
  private dmyMs(s: string | null): number | null {
    if (!s) return null;
    const m = s.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
    return m ? new Date(+m[3], +m[2] - 1, +m[1]).getTime() : null;
  }
  private typeKey(): string { return 'bpm.backlog.typeFilter.' + (this.projectId() || 'x'); }
  private statusKey(): string { return 'bpm.backlog.statusFilter.' + (this.projectId() || 'x'); }
  private asgKey(): string { return 'bpm.backlog.assignee.' + (this.projectId() || 'x'); }
  /** Nạp cấu hình lọc đã lưu của dự án (gọi khi có projectId). */
  private loadFilterPrefs(): void {
    const saved = loadPref<TaskType[] | null>(this.typeKey(), null);
    if (saved && saved.length) this.typeFilter.set(new Set(saved));
    const savedSt = loadPref<TaskStatus[] | null>(this.statusKey(), null);
    if (savedSt && savedSt.length) this.statusFilter.set(new Set(savedSt));
    // "Backlog của tôi": mặc định lọc theo tôi (không đọc/ghi pref chung của dự án để khỏi ảnh hưởng màn QLDA).
    const preset = this.presetAssignee();
    this.filterAssignee.set(preset ? preset : loadPref<string>(this.asgKey(), ''));
  }
  isTypeOn(t: TaskType): boolean { return this.typeFilter().has(t); }
  toggleType(t: TaskType): void {
    const s = new Set(this.typeFilter());
    s.has(t) ? s.delete(t) : s.add(t);
    if (s.size === 0) { this.allTypes.forEach((x) => s.add(x)); } // không cho ẩn hết
    this.typeFilter.set(s);
    savePref(this.typeKey(), [...s]);
  }
  toggleStatus(st: TaskStatus): void {
    const s = new Set(this.statusFilter());
    s.has(st) ? s.delete(st) : s.add(st);
    if (s.size === 0) { this.allStatuses.forEach((x) => s.add(x)); } // không cho ẩn hết
    this.statusFilter.set(s);
    savePref(this.statusKey(), [...s]);
  }
  setAssignee(uid: string): void {
    this.filterAssignee.set(uid || '');
    if (!this.presetAssignee()) savePref(this.asgKey(), uid || ''); // chế độ "Backlog của tôi" không ghi đè pref QLDA
  }
  /** Danh sách người để lọc — suy từ assignee của các task; kèm mục "Chưa gán". */
  readonly assigneeSel = computed<SelectOption[]>(() => {
    const map = new Map<string, string>();
    let hasUnassigned = false;
    for (const t of this.tasks()) {
      if (t.assigneeUserId) map.set(t.assigneeUserId, t.assigneeName || t.assigneeUserId);
      else hasUnassigned = true;
    }
    const people = [...map.entries()].map(([value, label]) => ({ value, label }))
      .sort((a, b) => a.label.localeCompare(b.label, 'vi'));
    // "Chưa gán" lên đầu để lọc nhanh các task chưa có người thực hiện.
    return hasUnassigned ? [{ value: this.UNASSIGNED, label: '— Chưa gán —' }, ...people] : people;
  });
  /** Làm tròn % về số nguyên (không lấy thập phân). */
  round(n: number | null | undefined): number { return Math.round(n || 0); }

  /** Danh sách phẳng DFS theo parentId + orderIndex, kèm cấp thụt lề. */
  readonly rows = computed<TreeRow[]>(() => {
    const byParent = new Map<string | null, ProjectTask[]>();
    for (const t of this.tasks()) {
      const k = t.parentId ?? null;
      (byParent.get(k) ?? byParent.set(k, []).get(k)!).push(t);
    }
    for (const list of byParent.values()) {
      list.sort((a, b) => (a.orderIndex - b.orderIndex) || (a.seq - b.seq));
    }
    const out: TreeRow[] = [];
    const walk = (parentId: string | null, level: number) => {
      for (const t of byParent.get(parentId) ?? []) {
        const kids = byParent.get(t.id) ?? [];
        out.push({ task: t, level, hasChildren: kids.length > 0 });
        walk(t.id, level + 1);
      }
    };
    walk(null, 0);
    return out;
  });

  /** Ẩn dòng nếu có tổ tiên đang gập, hoặc loại không nằm trong bộ lọc, hoặc không khớp người (vẫn giữ task CHA). */
  readonly filteredRows = computed<TreeRow[]>(() => {
    const types = this.typeFilter();
    const statuses = this.statusFilter();
    const asg = this.filterAssignee();
    const q = this.search().trim().toLowerCase();
    const byId = new Map(this.tasks().map((t) => [t.id, t]));

    // Thêm mọi tổ tiên của id vào set (để lọc vẫn thấy Epic/Story/task cha của task khớp).
    const addAncestors = (set: Set<string>, t: ProjectTask) => {
      set.add(t.id);
      let p = t.parentId;
      while (p) { set.add(p); p = byId.get(p)?.parentId ?? null; }
    };

    // Lọc NGƯỜI: '__UNASSIGNED__' = chưa gán; ngược lại = khớp userId. Giữ cả cấp cha.
    let assigneeKeep: Set<string> | null = null;
    if (asg) {
      assigneeKeep = new Set<string>();
      for (const t of this.tasks()) {
        const match = asg === this.UNASSIGNED ? !t.assigneeUserId : t.assigneeUserId === asg;
        if (match) addAncestors(assigneeKeep, t);
      }
    }

    // Lọc TRẠNG THÁI: nếu không phải "tất cả" → giữ task khớp trạng thái + MỌI cấp cha.
    let statusKeep: Set<string> | null = null;
    if (statuses.size < this.allStatuses.length) {
      statusKeep = new Set<string>();
      for (const t of this.tasks()) {
        if (statuses.has(t.status)) addAncestors(statusKeep, t);
      }
    }

    // TÌM theo từ khoá (tên/mã task) — giữ task khớp + MỌI cấp cha.
    let searchKeep: Set<string> | null = null;
    if (q) {
      searchKeep = new Set<string>();
      for (const t of this.tasks()) {
        if ((t.title || '').toLowerCase().includes(q) || (t.code || '').toLowerCase().includes(q)) {
          addAncestors(searchKeep, t);
        }
      }
    }

    // Lọc KHOẢNG NGÀY: task có [bắt đầu, kết thúc] chạm [from, to] — giữ cả cấp cha.
    let dateKeep: Set<string> | null = null;
    // Parse ISO (yyyy-MM-dd) theo LOCAL để khớp với dmyMs (cũng local) — tránh lệch múi giờ loại nhầm ngày biên.
    const isoMs = (iso: string): number | null => {
      const m = iso.match(/^(\d{4})-(\d{2})-(\d{2})$/);
      return m ? new Date(+m[1], +m[2] - 1, +m[3]).getTime() : null;
    };
    const fromMs = this.dateFrom() ? isoMs(this.dateFrom()) : null;
    const toMs = this.dateTo() ? (isoMs(this.dateTo())! + 86_400_000 - 1) : null;
    if (fromMs !== null || toMs !== null) {
      dateKeep = new Set<string>();
      for (const t of this.tasks()) {
        const s = this.dmyMs(t.startDate), d = this.dmyMs(t.dueDate);
        const ts = s ?? d, te = d ?? s;
        if (ts === null && te === null) continue;      // không có lịch → loại khi đang lọc ngày
        if (fromMs !== null && (te === null || te < fromMs)) continue;
        if (toMs !== null && (ts === null || ts > toMs)) continue;
        addAncestors(dateKeep, t);
      }
    }

    // Chips nhanh (AND, chỉ task LÁ): chưa gán / chưa có hạn / quá hạn — giữ cả cấp cha.
    let flagKeep: Set<string> | null = null;
    const flags = this.quickFlags();
    if (flags.size) {
      const parentIds = new Set(this.tasks().map((t) => t.parentId).filter(Boolean) as string[]);
      const isLeaf = (t: ProjectTask) => t.type !== 'EPIC' && t.type !== 'STORY' && !parentIds.has(t.id);
      const todayMs = new Date(new Date().toDateString()).getTime();
      flagKeep = new Set<string>();
      for (const t of this.tasks()) {
        if (!isLeaf(t)) continue;
        const due = this.dmyMs(t.dueDate);
        if (flags.has('noassignee') && t.assigneeUserId) continue;
        if (flags.has('nodeadline') && t.dueDate) continue;
        if (flags.has('overdue') &&
            !(due !== null && due < todayMs && t.status !== 'DONE' && t.status !== 'CANCELLED')) continue;
        addAncestors(flagKeep, t);
      }
    }

    return this.rows().filter((r) => {
      if (searchKeep && !searchKeep.has(r.task.id)) return false;
      if (!types.has(r.task.type)) return false;
      if (assigneeKeep && !assigneeKeep.has(r.task.id)) return false;
      if (statusKeep && !statusKeep.has(r.task.id)) return false;
      if (dateKeep && !dateKeep.has(r.task.id)) return false;
      if (flagKeep && !flagKeep.has(r.task.id)) return false;
      return true;
    });
  });

  /** Ẩn thêm dòng có tổ tiên đang GẬP (trên nền đã lọc) — dùng để HIỂN THỊ trên màn. */
  readonly visible = computed<TreeRow[]>(() => {
    const col = this.collapsed();
    const byId = new Map(this.tasks().map((t) => [t.id, t]));
    return this.filteredRows().filter((r) => {
      let p = r.task.parentId;
      while (p) {
        if (col.has(p)) return false;
        p = byId.get(p)?.parentId ?? null;
      }
      return true;
    });
  });

  /**
   * Tổng est (giờ) + % hoàn thành chung.
   * % chung: rollup theo est của các task GỐC (parentId=null) dùng progressPct mỗi gốc theo est.
   * progressPct của gốc đã là rollup theo est của cây con → đây là trọng số est chuẩn nhất.
   * Fallback: nếu tổng est = 0, lấy trung bình progressPct của task lá.
   */
  readonly summary = computed(() => {
    const ts = this.tasks();
    const tr = this.tree();
    // % HOÀN THÀNH — KHỚP HỆT màn Tổng quan (endpoint report): task LÁ (không có con) & KHÔNG Huỷ,
    //   trọng số = est THÔ (estimateHours). pct = Σ est(lá DONE)/Σ est(lá) ×100; est=0 → đếm số lá.
    const leaves = ts.filter((t) => !hasChildren(t.id, tr) && t.status !== 'CANCELLED');
    let leafEst = 0, leafDoneEst = 0, leafDone = 0;
    for (const t of leaves) {
      const w = t.estimateHours || 0;
      leafEst += w;
      if (t.status === 'DONE') { leafDone++; leafDoneEst += w; }
    }
    const pct = leaves.length === 0 ? 0
      : (leafEst > 0 ? Math.round((leafDoneEst / leafEst) * 100) : Math.round((leafDone / leaves.length) * 100));
    // % theo SỐ LƯỢNG task lá (đếm) — song song với % theo est ở trên.
    const taskPct = leaves.length === 0 ? 0 : Math.round((leafDone / leaves.length) * 100);
    return { total: ts.length, totalEst: Math.round(leafEst), leaves: leaves.length, leafDone, pct, taskPct };
  });

  constructor() {
    // Tải lại khi projectId đổi.
    effect(() => {
      const id = this.projectId();
      if (id) this.reload();
    });
  }

  ngOnInit(): void {
    this.loadFilterPrefs(); // khôi phục bộ lọc đã lưu của dự án (loại + người)
    // CHỈ thành viên dự án (không lấy toàn bộ nhân sự hệ thống).
    this.svc.listMembers(this.projectId()).subscribe({
      next: (m) => this.members.set(m),
      error: () => { /* không có thành viên — bỏ qua */ }
    });
  }

  reload(): void {
    this.loading.set(true);
    this.svc.listTasks(this.projectId()).subscribe({
      next: (r) => { this.tasks.set(r); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được danh sách công việc.'); this.loading.set(false); }
    });
  }

  /** Tải lại danh sách KHÔNG bật cờ loading (tránh nhấp nháy) — dùng sau thao tác inline để rollup %/est của cha cập nhật ngay. */
  private silentReload(): void {
    this.svc.listTasks(this.projectId()).subscribe({
      next: (r) => this.tasks.set(r),
      error: () => { /* giữ nguyên dữ liệu hiện tại nếu lỗi */ }
    });
  }

  // ===== Kéo-thả SẮP XẾP thứ tự (chỉ trong CÙNG CẤP: cùng parentId) =====
  readonly dragId = signal<string>('');
  readonly dragOverId = signal<string>('');

  private sameParent(a: ProjectTask, b: ProjectTask): boolean {
    return (a.parentId ?? null) === (b.parentId ?? null);
  }

  onDragStart(r: TreeRow, ev: DragEvent): void {
    this.dragId.set(r.task.id);
    if (ev.dataTransfer) { ev.dataTransfer.effectAllowed = 'move'; ev.dataTransfer.setData('text/plain', r.task.id); }
  }
  onDragOver(r: TreeRow, ev: DragEvent): void {
    const drag = this.tasks().find((t) => t.id === this.dragId());
    if (!drag || drag.id === r.task.id || !this.sameParent(drag, r.task)) return;
    ev.preventDefault();                        // chỉ cho THẢ khi cùng cấp
    if (ev.dataTransfer) ev.dataTransfer.dropEffect = 'move';
    this.dragOverId.set(r.task.id);
  }
  onDragLeave(r: TreeRow): void {
    if (this.dragOverId() === r.task.id) this.dragOverId.set('');
  }
  onDrop(r: TreeRow): void {
    const dragId = this.dragId();
    this.dragOverId.set('');
    this.dragId.set('');
    if (!dragId || dragId === r.task.id) return;
    const all = this.tasks();
    const drag = all.find((t) => t.id === dragId);
    if (!drag) return;
    if (!this.sameParent(drag, r.task)) {
      this.toast.warning('Chỉ sắp xếp thứ tự trong cùng cấp (cùng cha).');
      return;
    }
    // Nhóm anh em (cùng parentId) theo thứ tự hiện tại → di chuyển dragged tới vị trí target.
    const key = drag.parentId ?? null;
    const siblings = all.filter((t) => (t.parentId ?? null) === key)
      .sort((a, b) => (a.orderIndex - b.orderIndex) || (a.seq - b.seq));
    const from = siblings.findIndex((t) => t.id === dragId);
    const to = siblings.findIndex((t) => t.id === r.task.id);
    if (from < 0 || to < 0) return;
    siblings.splice(to, 0, siblings.splice(from, 1)[0]);
    const items: ReorderItem[] = siblings.map((t, i) => ({ taskId: t.id, parentId: t.parentId ?? null, orderIndex: i }));
    this.svc.reorderTasks(this.projectId(), items).subscribe({
      next: () => { this.toast.success('Đã sắp xếp lại thứ tự'); this.silentReload(); },
      error: (e) => this.toast.error('Không sắp xếp được', e?.error?.message ?? '')
    });
  }
  onDragEnd(): void { this.dragId.set(''); this.dragOverId.set(''); }

  // ----- Cây gập/mở -----
  isCollapsed(id: string): boolean { return this.collapsed().has(id); }
  toggle(id: string): void {
    const s = new Set(this.collapsed());
    s.has(id) ? s.delete(id) : s.add(id);
    this.collapsed.set(s);
  }
  expandAll(): void { this.collapsed.set(new Set()); }
  collapseAll(): void {
    this.collapsed.set(new Set(this.rows().filter((r) => r.hasChildren).map((r) => r.task.id)));
  }

  // ===== ROLLUP: task tự tổng hợp (có con HOẶC là EPIC/STORY) → khoá ô nhập =====
  /** true → cột người/est/ngày/status đều read-only, hiển thị giá trị tự tổng hợp. */
  isRollup(t: ProjectTask): boolean {
    return hasChildren(t.id, this.tree()) || t.type === 'EPIC' || t.type === 'STORY';
  }

  /** Task LÁ đang ở trạng thái Kiểm thử và KHÔNG phải BUG/ISSUE → cột hiển thị ô chọn NGƯỜI KIỂM THỬ. */
  isTesterCol(t: ProjectTask): boolean {
    return t.status === 'IN_REVIEW' && !this.isBugLike(t.type);
  }

  // ----- Modal -----
  openCreate(parentId: string | null): void {
    this.editingId.set(null);
    this.clearImages();
    this.f = this.emptyForm();
    this.f.parentId = parentId;
    if (parentId) {
      // Preset loại con hợp lệ đầu tiên theo loại cha (vd EPIC→STORY, STORY→TASK, TASK→SUBTASK).
      const parent = this.tasks().find((t) => t.id === parentId);
      this.f.type = this.firstChildTypeOf(parent?.type);
    } else {
      // Nút "+ Thêm task" ở toolbar → mặc định EPIC (gốc, không cha).
      this.f.type = 'EPIC';
    }
    // Tạo mới BUG/ISSUE → điền sẵn khung Mô tả lỗi (I. Các bước / II. Thực tế / III. Mong đợi).
    if (this.isBugLike(this.f.type)) this.f.description = BUG_DESCRIPTION_TEMPLATE;
    // task con của BUG/ISSUE kế thừa screen từ cha (gợi ý) — để trống mặc định.
    // Mặc định Ngày bắt đầu = Ngày kết thúc = HÔM NAY (vẫn cho sửa).
    this.startIso = this.dueIso = this.todayIso();
    this.modalOpen.set(true);
  }

  openEdit(t: ProjectTask): void {
    this.editingId.set(t.id);
    this.clearImages();
    this.f = {
      parentId: t.parentId,
      title: t.title,
      // Gộp 3 trường lỗi cũ (bước/thực tế/mong đợi) vào Mô tả — chỉ 1 lần, không mất dữ liệu.
      description: mergeBugFieldsIntoDescription(t),
      type: t.type,
      status: t.status,
      priority: t.priority,
      assigneeUserId: t.assigneeUserId,
      estimateHours: t.estimateHours,
      screen: t.screen ?? '',
      startDate: t.startDate,
      dueDate: t.dueDate,
      // Chi tiết lỗi (BUG/ISSUE) — severity/environment/screen giữ; 3 trường mô tả đã gộp vào Mô tả.
      severity: t.severity ?? null,
      stepsToReproduce: '',
      expectedResult: '',
      actualResult: '',
      environment: t.environment ?? ''
    };
    this.startIso = this.toIso(t.startDate);
    this.dueIso = this.toIso(t.dueDate);
    this.modalOpen.set(true);
  }

  save(): void {
    if (!this.f.title?.trim()) { this.toast.warning('Thiếu tiêu đề công việc'); return; }
    // Validate cha theo phân cấp: mọi loại KHÁC EPIC bắt buộc chọn cha ĐÚNG loại.
    const allow = this.parentTypeOf(this.f.type);
    if (allow) {
      const ok = this.f.parentId && this.tasks().some((t) => t.id === this.f.parentId && allow.includes(t.type));
      if (!ok) { this.toast.error(`Vui lòng chọn ${this.parentTypeLabel(this.f.type)} cha`); return; }
    } else {
      this.f.parentId = null; // EPIC → luôn là gốc
    }
    // Chặn est SUB-TASK ≤ 4h (UX sớm — BE cũng chặn).
    if (this.f.type === 'SUBTASK' && Number(this.f.estimateHours) > 4) {
      this.toast.error('Ước lượng sub-task không được quá 4 giờ');
      return;
    }
    this.saving.set(true);
    const bug = this.isBugLike(this.f.type);
    const sd = this.fromIso(this.startIso);
    const dd = this.fromIso(this.dueIso);
    // Est không nhập → tự tính theo duration × 8 (trừ SUBTASK do trần ≤ 4h).
    const durEst = this.f.type !== 'SUBTASK' ? this.daysBetween(sd, dd) * 8 : 0;
    const body: TaskRequest = {
      ...this.f,
      title: this.f.title.trim(),
      estimateHours: this.f.estimateHours ? Number(this.f.estimateHours) : durEst,
      startDate: sd,
      dueDate: dd,
      // Khối "Chi tiết lỗi" chỉ áp dụng cho BUG/ISSUE — loại khác gửi null (xoá dữ liệu cũ nếu đổi loại).
      screen: bug ? (this.f.screen || null) : null,
      severity: bug ? (this.f.severity || null) : null,
      // 3 trường lỗi đã gộp vào Mô tả → luôn gửi null (không dùng nữa).
      stepsToReproduce: null,
      expectedResult: null,
      actualResult: null,
      environment: bug ? (this.f.environment || null) : null
    };
    const id = this.editingId();
    const call = id
      ? this.svc.updateTask(this.projectId(), id, body)
      : this.svc.createTask(this.projectId(), body);
    call.subscribe({
      next: (saved) => {
        const taskId = id ?? saved.id;      // tạo mới → lấy id từ task trả về
        const imgs = this.previews();
        const done = (warn?: string) => {
          this.saving.set(false);
          this.clearImages();
          if (warn) { this.toast.warning(warn); } else { this.toast.success(id ? 'Đã cập nhật công việc' : 'Đã tạo công việc'); }
          this.reload();
          if (!id && this.keepAdding) {
            const { parentId, type, priority, assigneeUserId } = this.f;
            this.f = { ...this.emptyForm(), parentId, type, priority, assigneeUserId };
            this.startIso = this.dueIso = '';
            setTimeout(() => document.querySelector<HTMLInputElement>('#bl-form input[name=title]')?.focus(), 0);
          } else {
            this.modalOpen.set(false);
          }
        };
        // Có ảnh → upload vào task (id) rồi mới đóng; 1 ảnh lỗi không chặn ảnh khác.
        if (imgs.length && taskId) {
          forkJoin(imgs.map((p) => this.svc.uploadAttachment(this.projectId(), taskId, p.file).pipe(catchError(() => of(null)))))
            .subscribe((res) => done(res.filter((x) => x === null).length ? `Đã lưu; ${res.filter((x) => x === null).length}/${imgs.length} ảnh tải lỗi` : undefined));
        } else {
          done();
        }
      },
      error: (e) => { this.saving.set(false); this.toast.error('Không lưu được', e?.error?.message ?? ''); }
    });
  }

  /** Dựng TaskRequest ĐẦY ĐỦ từ task hiện có (giữ mọi field) + ghi đè patch. */
  private buildRequest(t: ProjectTask, patch: Partial<TaskRequest> = {}): TaskRequest {
    return {
      parentId: t.parentId, title: t.title, description: t.description ?? '',
      type: t.type, status: t.status, priority: t.priority,
      assigneeUserId: t.assigneeUserId, estimateHours: t.estimateHours,
      screen: t.screen, startDate: t.startDate, dueDate: t.dueDate,
      orderIndex: t.orderIndex,
      severity: t.severity, stepsToReproduce: t.stepsToReproduce,
      expectedResult: t.expectedResult, actualResult: t.actualResult, environment: t.environment,
      testerUserId: t.testerUserId ?? null,
      ...patch
    };
  }

  // ----- Thao tác nhanh trên dòng -----
  /** Đổi trạng thái: không chặn — chuyển thẳng. Sau khi sang Kiểm thử, cột người tự thành ô nhập NGƯỜI KIỂM THỬ. */
  changeStatus(t: ProjectTask, status: string): void {
    if (!status || status === t.status) return;
    this.svc.updateTaskStatus(this.projectId(), t.id, status as TaskStatus).subscribe({
      next: (u) => {
        this.patchLocal(u);
        this.toast.success('Đã đổi trạng thái', u.code);
        this.silentReload(); // tính lại % rollup của cha + tổng ngay (không cần reload tay)
      },
      error: (e) => this.toast.error('Không đổi được trạng thái', e?.error?.message ?? '')
    });
  }

  /** Gán NGƯỜI KIỂM THỬ tại cột (khi task lá ở trạng thái Kiểm thử) — updateTask giữ mọi field khác. */
  changeTester(t: ProjectTask, userId: string): void {
    const val = userId || null;
    if (val === (t.testerUserId ?? null)) return;
    this.svc.updateTask(this.projectId(), t.id, this.buildRequest(t, { testerUserId: val })).subscribe({
      next: (u) => { this.patchLocal(u); this.toast.success('Đã gán người kiểm thử', u.testerName ?? '— bỏ gán —'); },
      error: (e) => this.toast.error('Không gán được người kiểm thử', e?.error?.message ?? '')
    });
  }

  changeAssignee(t: ProjectTask, userId: string): void {
    const val = userId || null;
    if (val === t.assigneeUserId) return;
    this.svc.assignTask(this.projectId(), t.id, val).subscribe({
      next: (u) => { this.patchLocal(u); this.toast.success('Đã gán người thực hiện', u.assigneeName ?? '— bỏ gán —'); },
      error: (e) => this.toast.error('Không gán được', e?.error?.message ?? '')
    });
  }

  /**
   * Nhập nhanh est (giờ) tại chỗ trên lưới — CHỈ với task LÁ.
   * Gửi updateTask với đầy đủ field hiện có của task (title/type/status/priority/assignee/dates/screen)
   * + estimateHours mới → không xoá dữ liệu nào. Sau đó cập nhật local.
   */
  saveEstInline(t: ProjectTask, raw: string): void {
    if (this.isRollup(t)) return; // rollup (cha / EPIC / STORY) = tự tổng hợp, không nhập
    const val = Math.max(0, Number(raw) || 0);
    if (val === (t.estimateHours || 0)) return;
    // Chặn est SUB-TASK ≤ 4h (UX sớm — BE cũng chặn); revert bằng silentReload.
    if (t.type === 'SUBTASK' && val > 4) {
      this.toast.error('Ước lượng sub-task không được quá 4 giờ');
      this.silentReload(); // khôi phục giá trị cũ trên lưới
      return;
    }
    const body = this.buildRequest(t, { estimateHours: val });
    this.svc.updateTask(this.projectId(), t.id, body).subscribe({
      next: (u) => {
        this.patchLocal(u);
        this.toast.success('Đã cập nhật est', `${u.code}: ${val} giờ`);
        this.silentReload(); // est đổi → % rollup cha + tổng est tính lại ngay
      },
      error: (e) => this.toast.error('Không cập nhật được est', e?.error?.message ?? '')
    });
  }

  /** dd/MM/yyyy → yyyy-MM-dd cho <input type=date>; task CHƯA có ngày → mặc định HÔM NAY. */
  iso(d: string | null | undefined): string { return this.toIso(d ?? null) || this.todayIso(); }
  /** Hôm nay dạng dd/MM/yyyy (cho API). */
  private todayDmy(): string { return this.fromIso(this.todayIso())!; }

  /** Số ngày (gồm 2 đầu) giữa 2 ngày dd/MM/yyyy; 0 nếu thiếu/không hợp lệ. */
  private daysBetween(start: string | null, due: string | null): number {
    const s = this.parseDmy(start); const e = this.parseDmy(due);
    if (!s || !e || e < s) return 0;
    return Math.round((e.getTime() - s.getTime()) / 86400000) + 1;
  }

  /**
   * Cập nhật NGÀY bắt đầu/kết thúc ngay trên lưới (task LÁ). Nếu Est CHƯA nhập (=0) và có đủ 2 ngày
   * → tự tính Est = duration (số ngày) × 8. (SUBTASK giữ nhập tay do trần ≤ 4h.)
   */
  saveDateInline(t: ProjectTask, which: 'start' | 'due', iso: string): void {
    if (this.isRollup(t)) return; // cha/EPIC/STORY = ngày tự tổng hợp
    const dmy = this.fromIso(iso);
    // Ngày còn lại nếu task CHƯA có → mặc định HÔM NAY (khớp giá trị đang hiển thị trên lưới).
    const startDate = which === 'start' ? dmy : (t.startDate ?? this.todayDmy());
    const dueDate = which === 'due' ? dmy : (t.dueDate ?? this.todayDmy());
    if (startDate === t.startDate && dueDate === t.dueDate) return;
    let estimateHours = t.estimateHours;
    const dur = this.daysBetween(startDate, dueDate);
    const autoEst = (!t.estimateHours || t.estimateHours === 0) && dur > 0 && t.type !== 'SUBTASK';
    if (autoEst) estimateHours = dur * 8;
    const body = this.buildRequest(t, { startDate, dueDate, estimateHours });
    this.svc.updateTask(this.projectId(), t.id, body).subscribe({
      next: (u) => {
        this.patchLocal(u);
        this.toast.success('Đã cập nhật ngày',
          autoEst ? `${u.code}: est tự tính ${estimateHours}h (${dur} ngày × 8)` : u.code);
        this.silentReload(); // ngày/est đổi → rollup cha + tổng tính lại
      },
      error: (e) => { this.toast.error('Không cập nhật được ngày', e?.error?.message ?? ''); this.silentReload(); }
    });
  }

  /** Có task con không (dùng cho lưới / modal). */
  hasKids(taskId: string): boolean { return hasChildren(taskId, this.tree()); }

  /** Est hiển thị cho task CHA = Σ est lá trong cây con (rollup, read-only). */
  rollupEst(taskId: string): number { return Math.round(subtreeLeafEstimate(taskId, this.tree())); }

  /** Task có con? (dùng trong template cho cột ngày rollup). */
  hasChildrenRow(taskId: string): boolean { return hasChildren(taskId, this.tree()); }

  // ===== Ngày (rollup từ task con) + Duration =====
  private parseDmy(s: string | null | undefined): Date | null {
    if (!s) return null;
    const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(s);
    return m ? new Date(+m[3], +m[2] - 1, +m[1]) : null;
  }
  private fmtDmy(d: Date | null): string | null {
    if (!d) return null;
    const p = (n: number) => String(n).padStart(2, '0');
    return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()}`;
  }
  /** Ngày bắt đầu hiển thị: lá → của nó; cha → MIN ngày (của chính cha + mọi con, gồm Bug/Sub-task). */
  startOf(task: ProjectTask): string | null {
    return effectiveStart(task, this.tree());
  }
  /** Ngày kết thúc hiển thị: lá → của nó; cha → MAX ngày (của chính cha + mọi con, gồm Bug/Sub-task). */
  dueOf(task: ProjectTask): string | null {
    return effectiveDue(task, this.tree());
  }
  /** Duration (số ngày) từ ngày bắt đầu → kết thúc (gồm 2 đầu); '—' nếu thiếu. */
  durationOf(task: ProjectTask): string {
    const s = this.parseDmy(this.startOf(task));
    const e = this.parseDmy(this.dueOf(task));
    if (!s || !e || e < s) return '—';
    const days = Math.round((e.getTime() - s.getTime()) / 86400000) + 1;
    return days + ' ngày';
  }

  /**
   * Trong modal: rollup khi (đang sửa task CÓ con) HOẶC loại đang chọn là EPIC/STORY → khoá est/người.
   * Method thường (không computed) để re-đánh giá mỗi lần đổi f.type trong modal.
   */
  editingRollup(): boolean {
    const id = this.editingId();
    if (id && hasChildren(id, this.tree())) return true;
    return this.f.type === 'EPIC' || this.f.type === 'STORY';
  }

  remove(r: TreeRow): void {
    if (r.hasChildren) { this.toast.warning('Không thể xoá', 'Công việc còn có task con.'); return; }
    if (!confirm(`Xoá công việc ${r.task.code} — ${r.task.title}?`)) return;
    this.svc.deleteTask(this.projectId(), r.task.id).subscribe({
      next: () => { this.toast.success('Đã xoá công việc', r.task.code); this.reload(); },
      error: (e) => this.toast.error('Không xoá được', e?.error?.message ?? '')
    });
  }

  // ----- nhãn / badge -----
  typeLabel(t: TaskType): string { return this.typeOptions.find((o) => o.value === t)?.label ?? t; }
  statusLabel(s: TaskStatus): string { return this.statusOptions.find((o) => o.value === s)?.label ?? s; }
  priorityLabel(p: TaskPriority): string { return this.priorityOptions.find((o) => o.value === p)?.label ?? p; }

  typeBadge(t: TaskType): string {
    switch (t) {
      case 'EPIC': return 'badge--pending';
      case 'STORY': return 'badge--active';
      case 'BUG': return 'badge--cancel';
      case 'ISSUE': return 'badge--cancel';
      default: return 'badge--neutral';
    }
  }
  /** Class tô màu DÒNG theo loại để nhận diện: EPIC / Story / Task cha (có con). */
  rowTypeClass(r: TreeRow): string {
    if (r.task.type === 'EPIC') return 'bl-row--epic';
    if (r.task.type === 'STORY') return 'bl-row--story';
    if (r.task.type === 'TASK' && r.hasChildren) return 'bl-row--parent';
    return '';
  }
  statusBadge(s: TaskStatus): string {
    switch (s) {
      case 'DONE': return 'badge--active';
      case 'IN_PROGRESS': return 'badge--pending';
      case 'IN_REVIEW': return 'badge--pending';
      case 'CANCELLED': return 'badge--cancel';
      case 'TODO': return 'badge--neutral';
      default: return 'badge--neutral';
    }
  }
  priorityBadge(p: TaskPriority): string {
    switch (p) {
      case 'URGENT': return 'badge--cancel';
      case 'HIGH': return 'badge--pending';
      case 'MEDIUM': return 'badge--neutral';
      default: return 'badge--neutral';
    }
  }

  isBugLike(t: TaskType | null | undefined): boolean { return t === 'BUG' || t === 'ISSUE'; }

  /** Bấm tiêu đề task → báo cha mở chi tiết. */
  openDetail(t: ProjectTask): void { this.openTask.emit(t); }

  // ----- helpers -----
  private patchLocal(u: ProjectTask): void {
    this.tasks.update((ts) => ts.map((t) => (t.id === u.id ? u : t)));
  }

  private emptyForm(): TaskRequest {
    return {
      parentId: null, title: '', description: '', type: 'TASK', status: 'BACKLOG',
      // Est mặc định 4 giờ (log nhanh); vẫn cho sửa.
      priority: 'MEDIUM', assigneeUserId: null, estimateHours: 4, screen: '',
      startDate: null, dueDate: null,
      // Chi tiết lỗi (BUG/ISSUE) — mặc định rỗng, chỉ dùng khi loại là BUG/ISSUE.
      severity: null, stepsToReproduce: '', expectedResult: '', actualResult: '', environment: ''
    };
  }

  /** Hôm nay dạng yyyy-MM-dd (local) cho <input type=date>. */
  private todayIso(): string {
    const d = new Date();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${mm}-${dd}`;
  }

  /** dd/MM/yyyy → yyyy-MM-dd cho input date. */
  private toIso(d: string | null): string {
    if (!d) return '';
    const m = d.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
    return m ? `${m[3]}-${m[2]}-${m[1]}` : '';
  }
  /** yyyy-MM-dd → dd/MM/yyyy cho API. */
  private fromIso(d: string): string | null {
    if (!d) return null;
    const m = d.match(/^(\d{4})-(\d{2})-(\d{2})$/);
    return m ? `${m[3]}/${m[2]}/${m[1]}` : null;
  }
}
