import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageHeader } from '../shared/page-header/page-header';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { Modal } from '../shared/modal/modal';
import { SearchableSelect, SelectOption } from '../shared/searchable-select/searchable-select';
import { EmployeeChip } from '../shared/employee-chip/employee-chip';
import { Skeleton, EmptyState } from '../shared/skeleton/skeleton';
import { ToastService } from '../shared/toast/toast.service';
import {
  EmployeeService, Employee, EmployeeUpdate, EmployeeCreate, PreviewResponse, PreviewRow, ImportLog, SheetConfig
} from '../core/employee.service';

/**
 * Quản lý nhân sự + Import từ file (Epic 1 GĐ2 — chỉ admin).
 * Lưới + bộ lọc + modal xem chi tiết/sửa tay (14 trường) + modal "Nhập từ file" (xem trước → áp dụng) + nhật ký.
 */
@Component({
  selector: 'app-employees',
  imports: [FormsModule, PageHeader, DataGrid, GridCellDirective, Modal, SearchableSelect, EmployeeChip, Skeleton, EmptyState],
  templateUrl: './employees.html',
  styles: [`
    .emp-upload { display: grid; gap: var(--space-2); padding: var(--space-4);
      border: 1px dashed var(--color-border); border-radius: var(--radius-md);
      background: var(--color-surface-alt); }
    .emp-upload input[type=file] { font-size: var(--font-size-sm); }
    .emp-stats { display: flex; gap: var(--space-2); flex-wrap: wrap; margin: var(--space-3) 0 var(--space-2); }
    .emp-import-body { display: grid; gap: var(--space-3); width: 100%; }
    .emp-modes { display: flex; gap: var(--space-2); margin-bottom: var(--space-3); }
    .emp-history { margin-top: var(--space-4); }
    .emp-history summary { cursor: pointer; font-weight: 600; font-size: var(--font-size-sm); padding: var(--space-2) 0; }
    .emp-sheetcfg { display: grid; gap: var(--space-2); margin-top: var(--space-2); padding: var(--space-3);
      border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-surface-alt); }
    .emp-link { margin-top: var(--space-4); padding: var(--space-3); border: 1px solid var(--color-border);
      border-radius: var(--radius-md); background: var(--color-surface-alt); }
    .emp-link legend { padding: 0 var(--space-2); font-weight: 600; font-size: var(--font-size-sm); }
    .emp-ext-badge { margin-left: var(--space-2); }
    .emp-projs { display: inline-flex; flex-wrap: wrap; gap: 3px; }
  `]
})
export class Employees implements OnInit {
  private svc = inject(EmployeeService);
  private toast = inject(ToastService);

  // ----- Danh sách + lọc -----
  readonly rows = signal<Employee[]>([]);
  readonly loading = signal(true);

  filterStatus = '';
  filterDept = '';
  filterLevel = '';
  filterQ = '';
  // '' tất cả · 'yes' chỉ thuê ngoài · 'no' chỉ nội bộ (lọc phía client, không gọi lại server).
  readonly filterExternalSig = signal<'' | 'yes' | 'no'>('');

  // Tối giản: 1 cột "Nhân sự" (Mã · Tên · Vị trí · Bộ phận) + Level + Trạng thái + thao tác.
  readonly cols: GridColumn[] = [
    { key: 'employee', header: 'Nhân sự' },
    { key: 'projects', header: 'Dự án đang join', width: '220px' },
    { key: 'effort', header: 'Tổng effort', width: '110px', align: 'center', sortable: true },
    { key: 'joinDate', header: 'Ngày vào', width: '110px', sortable: true },
    { key: 'birthDate', header: 'Ngày sinh', width: '110px', sortable: true },
    { key: 'level', header: 'Level', width: '90px', sortable: true },
    { key: 'status', header: 'Trạng thái', width: '140px' },
    { key: 'actions', header: '', width: '120px' }
  ];

  /** Danh sách hiển thị = rows() đã áp thêm bộ lọc thuê-ngoài (client-side). */
  readonly displayRows = computed<Employee[]>(() => {
    const f = this.filterExternalSig();
    const rs = this.rows();
    if (f === 'yes') return rs.filter((e) => e.external);
    if (f === 'no') return rs.filter((e) => !e.external);
    return rs;
  });

  /** Gợi ý cho bộ lọc, suy ra từ dữ liệu đang có. */
  readonly statusOptions = computed(() => this.distinct((e) => e.status));
  readonly deptOptions = computed(() => this.distinct((e) => e.deptCode));
  readonly levelOptions = computed(() => this.distinct((e) => e.level));

  /** Option cho searchable-select bộ lọc. */
  readonly statusSel = computed<SelectOption[]>(() => this.statusOptions().map((s) => ({ value: s, label: s })));
  readonly deptSel = computed<SelectOption[]>(() => this.deptOptions().map((d) => ({ value: d, label: d })));
  readonly levelSel = computed<SelectOption[]>(() => this.levelOptions().map((l) => ({ value: l, label: l })));

  /** Thống kê nhanh trên danh sách đang hiển thị (theo bộ lọc hiện tại). */
  readonly stats = computed(() => {
    const rs = this.displayRows();
    const active = rs.filter((e) => (e.status || '').trim().toLowerCase().includes('đang làm')).length;
    const external = rs.filter((e) => e.external).length;
    return { total: rs.length, active, inactive: rs.length - active, external };
  });

  // ----- Chi tiết / sửa tay -----
  readonly detailOpen = signal(false);
  readonly detailTarget = signal<Employee | null>(null);
  readonly editing = signal(false);
  e: EmployeeUpdate = this.emptyEdit();

  // ----- Tạo mới thủ công (thuê ngoài/mượn) -----
  readonly createOpen = signal(false);
  readonly creating = signal(false);
  c: EmployeeCreate = this.emptyCreate();

  // ----- Import -----
  readonly importOpen = signal(false);
  readonly importMode = signal<'file' | 'sheet'>('file'); // tải file hoặc link Google Sheet
  readonly selectedFile = signal<File | null>(null);
  readonly sheetUrl = signal('');
  readonly autoSyncEnabled = signal(false);
  readonly syncTime = signal('02:00'); // giờ đồng bộ hàng ngày "HH:mm"
  readonly sheetCfg = signal<SheetConfig | null>(null);
  readonly preview = signal<PreviewResponse | null>(null);
  readonly previewing = signal(false);
  readonly applying = signal(false);
  readonly fullSync = signal(false); // đồng bộ toàn phần: khoá người vắng mặt khỏi file
  readonly logs = signal<ImportLog[]>([]);

  readonly previewRows = computed<PreviewRow[]>(() => {
    const p = this.preview();
    if (!p) return [];
    return [...p.add, ...p.update, ...p.handover, ...p.lock, ...p.errors];
  });

  // Bảng xem trước hiển thị ĐỦ các cột của file (cuộn ngang nếu rộng).
  readonly previewCols: GridColumn[] = [
    { key: 'action', header: 'Loại', width: '110px' },
    { key: 'empCode', header: 'ID', width: '80px', sortable: true },
    { key: 'status', header: 'Trạng thái', width: '120px' },
    { key: 'fullName', header: 'Họ tên', width: '190px', sortable: true },
    { key: 'jobPosition', header: 'Vị trí công việc', width: '180px' },
    { key: 'title', header: 'Chức danh', width: '120px' },
    { key: 'deptCode', header: 'Mã bộ phận', width: '110px' },
    { key: 'unit', header: 'Đơn vị', width: '90px' },
    { key: 'joinDate', header: 'Ngày tham gia', width: '120px' },
    { key: 'birthDate', header: 'Ngày sinh', width: '110px' },
    { key: 'phone', header: 'Số điện thoại', width: '120px' },
    { key: 'contractType', header: 'Loại hợp đồng', width: '200px' },
    { key: 'bankAccount', header: 'Số tài khoản', width: '150px' },
    { key: 'bankName', header: 'Ngân hàng', width: '200px' },
    { key: 'level', header: 'Level', width: '100px' },
    { key: 'message', header: 'Ghi chú', width: '200px' }
  ];

  readonly logCols: GridColumn[] = [
    { key: 'runAt', header: 'Thời điểm', width: '160px' },
    { key: 'runBy', header: 'Người chạy', width: '160px' },
    { key: 'fileName', header: 'Tệp' },
    { key: 'added', header: 'Thêm', align: 'center', width: '70px' },
    { key: 'updated', header: 'Sửa', align: 'center', width: '70px' },
    { key: 'locked', header: 'Khoá', align: 'center', width: '70px' },
    { key: 'handover', header: 'Bàn giao', align: 'center', width: '90px' },
    { key: 'errors', header: 'Lỗi', align: 'center', width: '70px' }
  ];

  ngOnInit(): void {
    this.reload();
    this.loadLogs();
  }

  reload(): void {
    this.loading.set(true);
    this.svc.list({
      status: this.filterStatus || undefined,
      deptCode: this.filterDept || undefined,
      level: this.filterLevel || undefined,
      q: this.filterQ || undefined
    }).subscribe({
      next: (r) => { this.rows.set(r); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được danh sách nhân sự (cần quyền quản trị).'); this.loading.set(false); }
    });
  }

  resetFilter(): void {
    this.filterStatus = this.filterDept = this.filterLevel = this.filterQ = '';
    this.filterExternalSig.set('');
    this.reload();
  }

  loadLogs(): void {
    this.svc.logs().subscribe({
      next: (l) => this.logs.set(l),
      error: () => { /* nhật ký rỗng — bỏ qua */ }
    });
  }

  // ----- Chi tiết / sửa -----
  /** Xoá nhân sự THUÊ NGOÀI (chỉ external). */
  removeExternal(emp: Employee): void {
    if (!emp.external) return;
    if (!confirm(`Xoá nhân sự thuê ngoài "${emp.fullName}" (${emp.empCode})? Sẽ gỡ khỏi mọi dự án + xoá tài khoản.`)) return;
    this.svc.delete(emp.id).subscribe({
      next: () => { this.toast.success('Đã xoá nhân sự thuê ngoài', emp.fullName); this.reload(); },
      error: (err) => this.toast.error('Không xoá được', err?.error?.message ?? err?.error?.detail ?? '')
    });
  }

  openDetail(emp: Employee): void {
    this.detailTarget.set(emp);
    this.editing.set(false);
    this.e = this.fromEmployee(emp);
    this.detailOpen.set(true);
    // Tải chi tiết đầy đủ để có liên thông (đường dẫn cây, vị trí, vai trò) — list không kèm các trường này.
    this.svc.get(emp.id).subscribe({
      next: (full) => { if (this.detailTarget()?.id === full.id) this.detailTarget.set(full); },
      error: () => { /* giữ dữ liệu từ list — bỏ qua */ }
    });
  }

  startEdit(): void { this.editing.set(true); }

  saveEdit(): void {
    const emp = this.detailTarget();
    if (!emp) return;
    if (!this.e.fullName?.trim()) { this.toast.warning('Thiếu họ tên'); return; }
    this.svc.update(emp.id, { ...this.e }).subscribe({
      next: (updated) => {
        this.toast.success('Đã cập nhật nhân sự', updated.empCode + ' · ' + updated.fullName);
        this.detailOpen.set(false);
        this.reload();
      },
      error: (err) => this.toast.error('Không cập nhật được', err?.error?.message ?? err?.error?.detail ?? '')
    });
  }

  // ----- Tạo mới thủ công (thuê ngoài/mượn) -----
  openCreate(): void {
    this.c = this.emptyCreate();
    this.createOpen.set(true);
  }

  saveCreate(): void {
    if (!this.c.empCode?.trim()) { this.toast.warning('Thiếu mã nhân sự (ID)'); return; }
    if (!this.c.fullName?.trim()) { this.toast.warning('Thiếu họ tên'); return; }
    this.creating.set(true);
    this.svc.create({ ...this.c, empCode: this.c.empCode.trim(), fullName: this.c.fullName.trim() }).subscribe({
      next: (created) => {
        this.creating.set(false);
        this.toast.success('Đã thêm nhân sự thuê ngoài', created.empCode + ' · ' + created.fullName);
        this.createOpen.set(false);
        this.reload();
      },
      error: (err) => {
        this.creating.set(false);
        this.toast.error('Không thêm được nhân sự', err?.error?.message ?? err?.error?.detail ?? '');
      }
    });
  }

  // ----- Import -----
  openImport(): void {
    this.selectedFile.set(null);
    this.preview.set(null);
    this.importOpen.set(true);
    this.loadLogs();         // hiện lịch sử import ngay trong modal (Việc 1)
    this.loadSheetConfig();  // nạp link đã lưu (Việc 3)
  }

  setMode(m: 'file' | 'sheet'): void {
    this.importMode.set(m);
    this.preview.set(null);
  }

  // ----- Link đã lưu / tự đồng bộ (Việc 3) -----
  loadSheetConfig(): void {
    this.svc.getSheetConfig().subscribe({
      next: (c) => {
        this.sheetCfg.set(c);
        if (c.sheetUrl) this.sheetUrl.set(c.sheetUrl);
        this.autoSyncEnabled.set(c.autoSync);
        this.syncTime.set(c.syncTime || '02:00');
      },
      error: () => {}
    });
  }

  saveSheetCfg(): void {
    const url = this.sheetUrl().trim();
    if (!url) { this.toast.warning('Dán link Google Sheet trước khi lưu'); return; }
    this.svc.saveSheetConfig({
      url, fullSync: this.fullSync(), autoSync: this.autoSyncEnabled(), syncTime: this.syncTime()
    }).subscribe({
      next: (c) => {
        this.sheetCfg.set(c);
        this.toast.success('Đã lưu link Google Sheet',
          c.autoSync ? `Tự đồng bộ hàng ngày lúc ${c.syncTime}` : 'Tự đồng bộ đang TẮT');
      },
      error: (e) => this.toast.error('Không lưu được link', e?.error?.message ?? '')
    });
  }

  syncSavedNow(): void {
    this.applying.set(true);
    this.svc.syncNow().subscribe({
      next: (r) => {
        this.applying.set(false);
        this.toast.success('Đã đồng bộ từ link đã lưu',
          `Thêm ${r.added} · Sửa ${r.updated} · Khoá ${r.locked} · Lỗi ${r.errors}`);
        this.importOpen.set(false);
        this.reload();
        this.loadLogs();
      },
      error: (e) => { this.applying.set(false); this.toast.error('Đồng bộ thất bại', e?.error?.message ?? ''); }
    });
  }

  onFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files && input.files[0] ? input.files[0] : null;
    this.selectedFile.set(file);
    this.preview.set(null);
  }

  runPreview(): void {
    const sheet = this.importMode() === 'sheet';
    if (sheet && !this.sheetUrl().trim()) { this.toast.warning('Dán link Google Sheet'); return; }
    if (!sheet && !this.selectedFile()) { this.toast.warning('Chọn file nhân sự (.xlsx hoặc .csv)'); return; }
    this.previewing.set(true);
    const call = sheet
      ? this.svc.previewSheet(this.sheetUrl().trim(), this.fullSync())
      : this.svc.preview(this.selectedFile()!, this.fullSync());
    call.subscribe({
      next: (p) => {
        this.preview.set(p);
        this.previewing.set(false);
        this.toast.info('Đã đọc dữ liệu',
          `Đọc ${p.totalRead} dòng · Thêm ${p.add.length} · Sửa ${p.update.length} · Khoá ${p.lock.length} · Bàn giao ${p.handover.length} · Lỗi ${p.errors.length}`);
      },
      error: (err) => {
        this.previewing.set(false);
        this.toast.error('Không đọc được dữ liệu', err?.error?.message ?? err?.error?.detail ?? 'Kiểm tra định dạng/tiêu đề / quyền chia sẻ');
      }
    });
  }

  applyImport(): void {
    const sheet = this.importMode() === 'sheet';
    const p = this.preview();
    if (!p) return;
    if ((sheet && !this.sheetUrl().trim()) || (!sheet && !this.selectedFile())) return;
    if (!confirm(`Áp dụng import?\nThêm ${p.add.length}, Sửa ${p.update.length}, Khoá ${p.lock.length}. ${p.handover.length} tài khoản cần bàn giao sẽ KHÔNG bị khoá.`)) {
      return;
    }
    this.applying.set(true);
    const call = sheet
      ? this.svc.applySheet(this.sheetUrl().trim(), this.fullSync())
      : this.svc.apply(this.selectedFile()!, this.fullSync());
    call.subscribe({
      next: (r) => {
        this.applying.set(false);
        this.toast.success('Đã import nhân sự',
          `Thêm ${r.added} · Sửa ${r.updated} · Khoá ${r.locked} · Bàn giao ${r.handover} · Lỗi ${r.errors}`);
        this.importOpen.set(false);
        this.reload();
        this.loadLogs();
      },
      error: (err) => {
        this.applying.set(false);
        this.toast.error('Import thất bại', err?.error?.message ?? err?.error?.detail ?? '');
      }
    });
  }

  // ----- nhãn/màu -----
  actionLabel(a: string): string {
    switch (a) {
      case 'ADD': return 'Thêm mới';
      case 'UPDATE': return 'Cập nhật';
      case 'LOCK': return 'Sẽ khoá';
      case 'HANDOVER': return 'Cần bàn giao';
      case 'ERROR': return 'Lỗi';
      default: return a;
    }
  }

  actionBadge(a: string): string {
    switch (a) {
      case 'ADD': return 'badge--active';
      case 'UPDATE': return 'badge--neutral';
      case 'LOCK': return 'badge--cancel';
      case 'HANDOVER': return 'badge--pending';
      case 'ERROR': return 'badge--cancel';
      default: return 'badge--neutral';
    }
  }

  statusBadge(status: string | null): string {
    return status && status.trim().toLowerCase() === 'đang làm việc' ? 'badge--active' : 'badge--cancel';
  }

  fmt(iso: string): string {
    return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  // ----- helpers -----
  private distinct(pick: (e: Employee) => string | null): string[] {
    const set = new Set<string>();
    for (const e of this.rows()) {
      const v = pick(e);
      if (v && v.trim()) set.add(v.trim());
    }
    return [...set].sort((a, b) => a.localeCompare(b, 'vi'));
  }

  private emptyCreate(): EmployeeCreate {
    return {
      empCode: '', status: 'Đang làm việc', fullName: '', jobPosition: '', title: '', deptCode: '', unit: '',
      joinDate: '', birthDate: '', phone: '', contractType: '', bankAccount: '', bankName: '', level: ''
    };
  }

  private emptyEdit(): EmployeeUpdate {
    return {
      status: '', fullName: '', jobPosition: '', title: '', deptCode: '', unit: '',
      joinDate: '', birthDate: '', phone: '', contractType: '', bankAccount: '', bankName: '', level: ''
    };
  }

  private fromEmployee(emp: Employee): EmployeeUpdate {
    return {
      status: emp.status ?? '', fullName: emp.fullName, jobPosition: emp.jobPosition ?? '',
      title: emp.title ?? '', deptCode: emp.deptCode ?? '', unit: emp.unit ?? '',
      joinDate: emp.joinDate ?? '', birthDate: emp.birthDate ?? '', phone: emp.phone ?? '',
      contractType: emp.contractType ?? '', bankAccount: emp.bankAccount ?? '',
      bankName: emp.bankName ?? '', level: emp.level ?? ''
    };
  }
}
