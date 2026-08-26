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
import { OtService, OtEntry, OtEntryRequest, OtRangeSummary } from '../core/ot.service';

/**
 * Công cụ Đăng ký OT (Epic 3, UX-DR6).
 * Tab "OT của tôi": form đăng ký + lưới của tôi, sửa/xoá khi kỳ chưa chốt.
 * Tab "Tổng hợp" (FEAT_OT_MANAGE): tổng hợp theo KHOẢNG NGÀY + Xuất Excel.
 */
@Component({
  selector: 'app-ot',
  imports: [FormsModule, PageHeader, DataGrid, GridCellDirective, Modal, ConfirmDialog, Tabs],
  templateUrl: './ot.html'
})
export class Ot implements OnInit {
  private svc = inject(OtService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  readonly canManage = computed(() => this.auth.hasFeature('OT_MANAGE'));

  readonly tabs = computed<TabItem[]>(() => {
    const t: TabItem[] = [{ key: 'mine', label: 'OT của tôi', icon: '🕒' }];
    if (this.canManage()) t.push({ key: 'summary', label: 'Tổng hợp', icon: '📊' });
    return t;
  });
  readonly tab = signal('mine');

  // ===== Tab OT của tôi =====
  readonly entryCols: GridColumn[] = [
    { key: 'workDate', header: 'Ngày', sortable: true, width: '124px' },
    { key: 'time', header: 'Giờ', width: '140px' },
    { key: 'hours', header: 'Số giờ', align: 'center', width: '90px' },
    { key: 'reason', header: 'Lý do / Dự án', sortable: true },
    { key: 'periodKey', header: 'Kỳ', align: 'center', width: '90px' },
    { key: 'actions', header: '', width: '120px' }
  ];
  readonly entries = signal<OtEntry[]>([]);
  readonly loading = signal(true);

  readonly formOpen = signal(false);
  readonly editId = signal<string | null>(null);
  f: OtEntryRequest = this.blankForm();

  // Xoá
  readonly confirmDeleteOpen = signal(false);
  private toDelete: OtEntry | null = null;

  // ===== Tab Tổng hợp theo KHOẢNG NGÀY (FEAT_OT_MANAGE) =====
  readonly empCols: GridColumn[] = [
    { key: 'userName', header: 'Tên', sortable: true },
    { key: 'orgUnitName', header: 'Bộ phận', sortable: true, width: '200px' },
    { key: 'totalHours', header: 'Tổng giờ OT', align: 'center', sortable: true, width: '130px' },
    { key: 'entryCount', header: 'Số lần', align: 'center', width: '90px' }
  ];
  readonly from = signal(this.firstOfMonth());
  readonly to = signal(this.lastOfMonth());
  readonly summary = signal<OtRangeSummary | null>(null);
  readonly summaryLoading = signal(false);

  ngOnInit(): void {
    this.reloadMine();
  }

  // ---------- OT của tôi ----------
  private blankForm(): OtEntryRequest {
    const today = todayIso();
    const t = this.defaultTimes(today);
    return { workDate: today, startTime: t.start, endTime: t.end, hours: null, reason: '', note: '' };
  }

  /** T7/CN? (theo chuỗi yyyy-MM-dd). */
  isWeekendStr(dateStr: string): boolean {
    if (!dateStr) return false;
    const day = new Date(dateStr + 'T00:00:00').getDay(); // 0=CN, 6=T7
    return day === 0 || day === 6;
  }

  /** Giờ mặc định: ngày thường 17:30–18:30; T7/CN 08:30–17:30. */
  private defaultTimes(dateStr: string): { start: string; end: string } {
    return this.isWeekendStr(dateStr) ? { start: '08:30', end: '17:30' } : { start: '17:30', end: '18:30' };
  }

  /** Khi đổi ngày → tự điền lại giờ mặc định theo ngày thường / cuối tuần. */
  onWorkDateChange(): void {
    const t = this.defaultTimes(this.f.workDate);
    this.f.startTime = t.start;
    this.f.endTime = t.end;
  }

  /** Số giờ OT tự tính = giờ cuối − giờ đầu; T7/CN trừ 1.5h nghỉ trưa. */
  previewHours(): number {
    const s = this.f.startTime;
    const e = this.f.endTime;
    if (!s || !e) return Number(this.f.hours) || 0;
    const [sh, sm] = s.split(':').map(Number);
    const [eh, em] = e.split(':').map(Number);
    let mins = (eh * 60 + em) - (sh * 60 + sm);
    if (mins < 0) mins += 24 * 60;
    let h = mins / 60;
    if (this.isWeekendStr(this.f.workDate)) {
      // T7/CN: trừ 1.5h nếu bắt đầu ≤ 08:00; muộn hơn (vd 08:30) trừ 1h
      h -= (sh * 60 + sm) <= 8 * 60 ? 1.5 : 1.0;
    }
    return Math.max(0, Math.round(h * 100) / 100);
  }

  reloadMine(): void {
    this.loading.set(true);
    this.svc.myEntries().subscribe({
      next: (r) => { this.entries.set(r); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được đăng ký OT'); this.loading.set(false); }
    });
  }

  openCreate(): void {
    this.editId.set(null);
    this.f = this.blankForm();
    this.formOpen.set(true);
  }

  openEdit(e: OtEntry): void {
    if (e.closed) { this.toast.error('Kỳ đã chốt', 'Không thể sửa đăng ký này.'); return; }
    this.editId.set(e.id);
    this.f = {
      workDate: e.workDate,
      startTime: e.startTime ? e.startTime.slice(0, 5) : '',
      endTime: e.endTime ? e.endTime.slice(0, 5) : '',
      hours: e.startTime && e.endTime ? null : e.hours,
      reason: e.reason ?? '',
      note: e.note ?? ''
    };
    this.formOpen.set(true);
  }

  save(): void {
    const req: OtEntryRequest = {
      workDate: this.f.workDate,
      startTime: this.f.startTime || null,
      endTime: this.f.endTime || null,
      hours: this.f.hours != null && this.f.hours !== ('' as unknown as number) ? Number(this.f.hours) : null,
      reason: this.f.reason || null,
      note: this.f.note || null
    };
    if (!req.workDate) { this.toast.error('Thiếu ngày làm thêm'); return; }
    if (!req.startTime && !req.hours) { this.toast.error('Nhập giờ bắt đầu/kết thúc hoặc số giờ'); return; }

    const id = this.editId();
    const obs = id ? this.svc.update(id, req) : this.svc.register(req);
    obs.subscribe({
      next: () => {
        this.toast.success(id ? 'Đã cập nhật đăng ký OT' : 'Đã đăng ký OT');
        this.formOpen.set(false);
        this.reloadMine();
      },
      error: (e) => this.toast.error('Không lưu được đăng ký', e?.error?.message || '')
    });
  }

  askDelete(e: OtEntry): void {
    if (e.closed) { this.toast.error('Kỳ đã chốt', 'Không thể xoá đăng ký này.'); return; }
    this.toDelete = e;
    this.confirmDeleteOpen.set(true);
  }

  confirmDelete(): void {
    const e = this.toDelete;
    this.confirmDeleteOpen.set(false);
    if (!e) return;
    this.svc.remove(e.id).subscribe({
      next: () => { this.toast.success('Đã xoá đăng ký OT'); this.reloadMine(); },
      error: (err) => this.toast.error('Không xoá được', err?.error?.message || '')
    });
  }

  // ---------- Tổng hợp theo khoảng ngày ----------
  loadSummary(): void {
    const from = this.from();
    const to = this.to();
    if (!from || !to) { this.toast.error('Chọn khoảng ngày'); return; }
    if (from > to) { this.toast.error('"Từ ngày" phải ≤ "Đến ngày"'); return; }
    this.summaryLoading.set(true);
    this.svc.summaryRange(from, to).subscribe({
      next: (s) => { this.summary.set(s); this.summaryLoading.set(false); },
      error: () => { this.toast.error('Không tải được tổng hợp OT'); this.summaryLoading.set(false); }
    });
  }

  exportXlsx(): void {
    const from = this.from();
    const to = this.to();
    if (!from || !to) return;
    window.open(this.svc.exportRangeUrl(from, to), '_blank');
  }

  // ---------- helpers ----------
  timeLabel(e: OtEntry): string {
    if (e.startTime && e.endTime) return `${e.startTime.slice(0, 5)}–${e.endTime.slice(0, 5)}`;
    return '—';
  }

  private firstOfMonth(): string {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1).toLocaleDateString('en-CA');
  }
  private lastOfMonth(): string {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth() + 1, 0).toLocaleDateString('en-CA');
  }
}
