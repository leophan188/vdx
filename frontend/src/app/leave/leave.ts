import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageHeader } from '../shared/page-header/page-header';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { Modal } from '../shared/modal/modal';
import { ConfirmDialog } from '../shared/confirm-dialog/confirm-dialog';
import { Tabs, TabItem } from '../shared/tabs/tabs';
import { ToastService } from '../shared/toast/toast.service';
import { todayIso } from '../shared/format';
import { AuthService } from '../core/auth.service';
import { LeaveService, LeaveEntry, LeaveEntryRequest, LeaveSummary, workdays } from '../core/leave.service';

/**
 * Đăng ký nghỉ (ghi nhận, không phê duyệt).
 * Tab "Đăng ký của tôi" (FEAT_LEAVE): form đăng ký + lưới của tôi, sửa/xoá đơn của mình.
 * Tab "Tổng hợp" (FEAT_LEAVE_MANAGE): lọc theo khoảng ngày + bảng theo nhân sự + Xuất Excel.
 */
@Component({
  selector: 'app-leave',
  imports: [FormsModule, PageHeader, DataGrid, GridCellDirective, Modal, ConfirmDialog, Tabs],
  templateUrl: './leave.html'
})
export class Leave implements OnInit {
  private svc = inject(LeaveService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  readonly canManage = computed(() => this.auth.hasFeature('LEAVE_MANAGE'));

  readonly tabs = computed<TabItem[]>(() => {
    const t: TabItem[] = [{ key: 'mine', label: 'Đăng ký của tôi', icon: '🌴' }];
    if (this.canManage()) t.push({ key: 'summary', label: 'Tổng hợp', icon: '📊' });
    return t;
  });
  readonly tab = signal('mine');

  // ===== Tab Đăng ký của tôi =====
  readonly entryCols: GridColumn[] = [
    { key: 'fromDate', header: 'Từ ngày', sortable: true, width: '124px' },
    { key: 'toDate', header: 'Đến ngày', sortable: true, width: '124px' },
    { key: 'type', header: 'Loại', align: 'center', width: '130px' },
    { key: 'days', header: 'Số ngày', align: 'center', sortable: true, width: '90px' },
    { key: 'reason', header: 'Lý do', sortable: true },
    { key: 'actions', header: '', width: '110px' }
  ];
  readonly entries = signal<LeaveEntry[]>([]);
  readonly loading = signal(true);

  readonly formOpen = signal(false);
  readonly editId = signal<string | null>(null);
  f: LeaveEntryRequest = this.blankForm();

  // Xoá
  readonly confirmDeleteOpen = signal(false);
  private toDelete: LeaveEntry | null = null;

  // ===== Tab Tổng hợp (FEAT_LEAVE_MANAGE) =====
  readonly empCols: GridColumn[] = [
    { key: 'userName', header: 'Tên', sortable: true },
    { key: 'orgUnitName', header: 'Bộ phận', sortable: true, width: '200px' },
    { key: 'annualDays', header: 'Phép năm (ngày)', align: 'center', sortable: true, width: '140px' },
    { key: 'unpaidDays', header: 'Không lương (ngày)', align: 'center', sortable: true, width: '150px' },
    { key: 'totalDays', header: 'Tổng (ngày)', align: 'center', sortable: true, width: '120px' },
    { key: 'entryCount', header: 'Số đơn', align: 'center', width: '90px' }
  ];
  readonly from = signal(this.firstOfMonth());
  readonly to = signal(this.lastOfMonth());
  readonly summary = signal<LeaveSummary | null>(null);
  readonly summaryLoading = signal(false);

  ngOnInit(): void {
    this.reloadMine();
  }

  // ---------- Đăng ký của tôi ----------
  private blankForm(): LeaveEntryRequest {
    const today = todayIso();
    return { fromDate: today, toDate: today, type: 'ANNUAL', reason: '' };
  }

  /** Xem trước số ngày nghỉ = đếm T2–T6 trong [từ,đến] (client-side, server tự tính khi lưu). */
  previewDays(): number {
    return workdays(this.f.fromDate, this.f.toDate);
  }

  reloadMine(): void {
    this.loading.set(true);
    this.svc.myEntries().subscribe({
      next: (r) => { this.entries.set(r); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được đơn nghỉ'); this.loading.set(false); }
    });
  }

  openCreate(): void {
    this.editId.set(null);
    this.f = this.blankForm();
    this.formOpen.set(true);
  }

  openEdit(e: LeaveEntry): void {
    this.editId.set(e.id);
    this.f = { fromDate: e.fromDate, toDate: e.toDate, type: e.type, reason: e.reason ?? '' };
    this.formOpen.set(true);
  }

  save(): void {
    const req: LeaveEntryRequest = {
      fromDate: this.f.fromDate,
      toDate: this.f.toDate,
      type: this.f.type,
      reason: this.f.reason || null
    };
    if (!req.fromDate || !req.toDate) { this.toast.error('Thiếu ngày nghỉ'); return; }
    if (req.fromDate > req.toDate) { this.toast.error('"Từ ngày" phải ≤ "Đến ngày"'); return; }

    const id = this.editId();
    const obs = id ? this.svc.update(id, req) : this.svc.register(req);
    obs.subscribe({
      next: () => {
        this.toast.success(id ? 'Đã cập nhật đơn nghỉ' : 'Đã ghi nhận đơn nghỉ');
        this.formOpen.set(false);
        this.reloadMine();
      },
      error: (e) => this.toast.error('Không lưu được đơn nghỉ', e?.error?.message || '')
    });
  }

  askDelete(e: LeaveEntry): void {
    this.toDelete = e;
    this.confirmDeleteOpen.set(true);
  }

  confirmDelete(): void {
    const e = this.toDelete;
    this.confirmDeleteOpen.set(false);
    if (!e) return;
    this.svc.remove(e.id).subscribe({
      next: () => { this.toast.success('Đã xoá đơn nghỉ'); this.reloadMine(); },
      error: (err) => this.toast.error('Không xoá được', err?.error?.message || '')
    });
  }

  // ---------- Tổng hợp ----------
  loadSummary(): void {
    const from = this.from();
    const to = this.to();
    if (!from || !to) { this.toast.error('Chọn khoảng ngày'); return; }
    if (from > to) { this.toast.error('"Từ ngày" phải ≤ "Đến ngày"'); return; }
    this.summaryLoading.set(true);
    this.svc.summary(from, to).subscribe({
      next: (s) => { this.summary.set(s); this.summaryLoading.set(false); },
      error: () => { this.toast.error('Không tải được tổng hợp nghỉ'); this.summaryLoading.set(false); }
    });
  }

  exportXlsx(): void {
    const from = this.from();
    const to = this.to();
    if (!from || !to) return;
    window.open(this.svc.exportUrl(from, to), '_blank');
  }

  // ---------- helpers ----------
  private firstOfMonth(): string {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1).toLocaleDateString('en-CA');
  }
  private lastOfMonth(): string {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth() + 1, 0).toLocaleDateString('en-CA');
  }
}
