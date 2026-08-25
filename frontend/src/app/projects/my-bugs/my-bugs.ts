import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { PageHeader } from '../../shared/page-header/page-header';
import { DataGrid, GridColumn } from '../../shared/data-grid/data-grid';
import { GridCellDirective } from '../../shared/data-grid/grid-cell.directive';
import { Modal } from '../../shared/modal/modal';
import { SearchableSelect, SelectOption } from '../../shared/searchable-select/searchable-select';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { memberPersonOptions } from '../../shared/person-options';
import { ToastService } from '../../shared/toast/toast.service';
import { ProjectService, ProjectMember, TaskStatus, TaskPriority, BugSeverity } from '../../core/project.service';
import { ProjectOption } from '../../core/me-task.service';
import { MeBugService, MyBug, MyBugType, MyBugRole, LogBugRequest } from '../../core/me-bug.service';

/**
 * BUG/ISSUE CỦA TÔI — màn cá nhân xuyên dự án.
 * - Log bug/issue mới (chọn dự án → nạp thành viên dự án để chọn PIC).
 * - Bảng MyBug: lọc theo Vai trò / Trạng thái / Loại. Bấm dòng → mở workspace dự án.
 * Ghi chú nghiệp vụ: người log là reporter; khi PIC chuyển Kiểm thử, bug tự trả về reporter để verify.
 */
@Component({
  selector: 'app-my-bugs',
  imports: [FormsModule, PageHeader, DataGrid, GridCellDirective, Modal, SearchableSelect, EmployeeChip],
  templateUrl: './my-bugs.html',
  styles: [`
    .mybug-form { display: grid; gap: var(--space-3); width: 100%; }
    .field-hint { font-size: var(--text-xs); color: var(--color-text-muted); margin-top: 2px; }
    .mybug-note { font-size: var(--text-xs); color: var(--color-text-muted);
      background: var(--color-surface-alt); border: 1px solid var(--color-border);
      border-radius: var(--radius-md); padding: 8px 10px; }
    .mybug-open { background: none; border: none; padding: 0; color: var(--color-primary);
      cursor: pointer; font-weight: 600; text-align: left; }
    .role { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: .72rem; font-weight: 600; line-height: 18px; white-space: nowrap; }
    .role--rep { background: var(--type-epic-bg); color: var(--type-epic); }
    .role--asg { background: var(--type-story-bg); color: var(--type-story); }
    .role--both { background: var(--status-done-bg); color: var(--status-done); }
  `]
})
export class MyBugs implements OnInit {
  private meBug = inject(MeBugService);
  private projectApi = inject(ProjectService);
  private toast = inject(ToastService);
  private router = inject(Router);

  readonly bugs = signal<MyBug[]>([]);
  readonly loading = signal(true);
  readonly projects = signal<ProjectOption[]>([]);
  /** Thành viên của dự án ĐANG CHỌN trong form (để chọn PIC). */
  readonly members = signal<ProjectMember[]>([]);

  // ----- Bộ lọc -----
  readonly filterRole = signal<string>('');
  readonly filterStatus = signal<string>('');
  readonly filterType = signal<string>('');

  readonly roleOptions: { value: MyBugRole; label: string }[] = [
    { value: 'REPORTER', label: 'Tôi log' }, { value: 'ASSIGNEE', label: 'Tôi xử lý' },
    { value: 'BOTH', label: 'Cả hai' }
  ];
  readonly typeOptions: { value: MyBugType; label: string }[] = [
    { value: 'BUG', label: 'Bug' }, { value: 'ISSUE', label: 'Issue' }
  ];
  readonly statusOptions: { value: TaskStatus; label: string }[] = [
    { value: 'BACKLOG', label: 'Backlog' }, { value: 'TODO', label: 'To Do' },
    { value: 'IN_PROGRESS', label: 'In Progress' }, { value: 'IN_REVIEW', label: 'Testing' },
    { value: 'DONE', label: 'Done' }, { value: 'CANCELLED', label: 'Cancelled' }
  ];
  readonly priorityOptions: { value: TaskPriority; label: string }[] = [
    { value: 'LOW', label: 'Low' }, { value: 'MEDIUM', label: 'Medium' },
    { value: 'HIGH', label: 'High' }, { value: 'URGENT', label: 'Urgent' }
  ];

  /** Mức độ nghiêm trọng (BUG/ISSUE) — đồng bộ task-detail. */
  readonly severityOptions: { value: BugSeverity; label: string }[] = [
    { value: 'BLOCKER', label: 'Blocker' }, { value: 'CRITICAL', label: 'Critical' },
    { value: 'MAJOR', label: 'Major' }, { value: 'MINOR', label: 'Minor' },
    { value: 'TRIVIAL', label: 'Trivial' }
  ];
  readonly severitySel: SelectOption[] = this.severityOptions.map((o) => ({ value: o.value, label: o.label }));

  readonly roleSel: SelectOption[] = this.roleOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly typeSel: SelectOption[] = this.typeOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly statusSel: SelectOption[] = this.statusOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly prioritySel: SelectOption[] = this.priorityOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly projectSel = computed<SelectOption[]>(() =>
    this.projects().map((p) => ({ value: p.id, label: p.code + ' · ' + p.name })));
  readonly peopleSel = computed<SelectOption[]>(() => memberPersonOptions(this.members()));

  readonly cols: GridColumn[] = [
    // Tiêu đề cố ý không đặt width — data-grid cho cột co giãn nuốt hết chỗ thừa.
    { key: 'code', header: 'Mã', width: '80px', sortable: true },
    { key: 'title', header: 'Tiêu đề', sortable: true },
    { key: 'projectName', header: 'Dự án', width: '180px', sortable: true },
    { key: 'type', header: 'Loại', width: '84px' },
    { key: 'status', header: 'Trạng thái', width: '132px' },
    { key: 'assigneeName', header: 'PIC', width: '168px' },
    { key: 'reporterName', header: 'Người log', width: '168px' },
    { key: 'role', header: 'Vai trò', width: '110px' },
    { key: 'dueDate', header: 'Deadline', width: '104px', sortable: true }
  ];

  readonly filtered = computed<MyBug[]>(() =>
    this.bugs().filter((b) =>
      (!this.filterRole() || b.role === this.filterRole()) &&
      (!this.filterStatus() || b.status === this.filterStatus()) &&
      (!this.filterType() || b.type === this.filterType())));

  // ----- Modal log bug -----
  readonly modalOpen = signal(false);
  readonly saving = signal(false);
  f: LogBugRequest = this.emptyForm();

  ngOnInit(): void {
    this.reload();
    this.meBug.myProjects().subscribe({
      next: (ps) => this.projects.set(ps),
      error: () => { /* bỏ qua */ }
    });
  }

  reload(): void {
    this.loading.set(true);
    this.meBug.list().subscribe({
      next: (r) => { this.bugs.set(r); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được danh sách bug/issue.'); this.loading.set(false); }
    });
  }

  resetFilter(): void {
    this.filterRole.set('');
    this.filterStatus.set('');
    this.filterType.set('');
  }

  // ----- Modal -----
  openLog(): void {
    this.f = this.emptyForm();
    this.members.set([]);
    this.modalOpen.set(true);
  }

  /** Khi đổi dự án → nạp thành viên dự án để chọn PIC; reset PIC đang chọn. */
  onProjectChange(projectId: string): void {
    this.f.projectId = projectId;
    this.f.assigneeUserId = null;
    this.members.set([]);
    if (!projectId) return;
    this.projectApi.listMembers(projectId).subscribe({
      next: (m) => this.members.set(m),
      error: () => this.members.set([])
    });
  }

  save(): void {
    if (!this.f.projectId) { this.toast.warning('Bắt buộc chọn dự án'); return; }
    if (!this.f.title?.trim()) { this.toast.warning('Thiếu tiêu đề'); return; }
    this.saving.set(true);
    const bug = this.isBugLike(this.f.type);
    const body: LogBugRequest = {
      ...this.f,
      title: this.f.title.trim(),
      description: this.f.description?.trim() || null,
      dueDate: this.f.dueDate || null,
      // Chi tiết lỗi — chỉ gửi khi BUG/ISSUE (loại khác gửi null).
      severity: bug ? (this.f.severity || null) : null,
      screen: bug ? (this.f.screen?.trim() || null) : null,
      environment: bug ? (this.f.environment?.trim() || null) : null,
      stepsToReproduce: bug ? (this.f.stepsToReproduce?.trim() || null) : null,
      expectedResult: bug ? (this.f.expectedResult?.trim() || null) : null,
      actualResult: bug ? (this.f.actualResult?.trim() || null) : null
    };
    this.meBug.log(body).subscribe({
      next: (r) => {
        this.saving.set(false);
        this.toast.success('Đã log bug/issue', this.f.title.trim());
        this.modalOpen.set(false);
        this.reload();
      },
      error: (e) => { this.saving.set(false); this.toast.error('Không log được', e?.error?.message ?? ''); }
    });
  }

  // ----- Điều hướng -----
  openWorkspace(b: MyBug): void {
    if (b.projectId) this.router.navigate(['/projects', b.projectId]);
  }

  // ----- nhãn / badge -----
  typeLabel(t: MyBugType): string { return this.typeOptions.find((o) => o.value === t)?.label ?? t; }
  statusLabel(s: TaskStatus): string { return this.statusOptions.find((o) => o.value === s)?.label ?? s; }
  roleLabel(r: MyBugRole): string { return this.roleOptions.find((o) => o.value === r)?.label ?? r; }

  typeBadge(t: MyBugType): string { return t === 'ISSUE' ? 'badge--pending' : 'badge--cancel'; }
  statusBadge(s: TaskStatus): string {
    switch (s) {
      case 'DONE': return 'badge--active';
      case 'IN_PROGRESS': return 'badge--pending';
      case 'IN_REVIEW': return 'badge--pending';
      case 'CANCELLED': return 'badge--cancel';
      default: return 'badge--neutral';
    }
  }
  roleBadge(r: MyBugRole): string {
    switch (r) {
      case 'REPORTER': return 'role role--rep';
      case 'ASSIGNEE': return 'role role--asg';
      default: return 'role role--both';
    }
  }

  /** Loại là BUG/ISSUE → hiện khối "Chi tiết lỗi" (ở màn này luôn đúng). */
  isBugLike(t: MyBugType | null | undefined): boolean { return t === 'BUG' || t === 'ISSUE'; }

  private emptyForm(): LogBugRequest {
    return {
      projectId: '', type: 'BUG', title: '', description: '',
      priority: 'MEDIUM', assigneeUserId: null, estimateHours: null, dueDate: null,
      // Chi tiết lỗi (BUG/ISSUE)
      severity: null, screen: '', environment: '',
      stepsToReproduce: '', expectedResult: '', actualResult: ''
    };
  }
}
