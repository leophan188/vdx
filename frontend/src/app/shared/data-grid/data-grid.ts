import { Component, computed, contentChild, contentChildren, input, signal, TemplateRef } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { GridCellDirective } from './grid-cell.directive';
import { GridDetailDirective } from './grid-detail.directive';

export interface GridColumn {
  key: string;
  header: string;
  sortable?: boolean;
  align?: 'left' | 'right' | 'center';
  width?: string;
}

type SortDir = 'asc' | 'desc' | null;

/**
 * Lưới dữ liệu dùng chung (DESIGN-SYSTEM §3): toolbar (tiêu đề + slot [gridFilters] + tìm kiếm +
 * slot nút [gridActions]), sort theo cột, phân trang + chọn số dòng/trang, hàng mở rộng chi tiết
 * (gridDetail), custom cell qua <ng-template gridCell="key" let-row>.
 */
@Component({
  selector: 'data-grid',
  imports: [NgTemplateOutlet],
  templateUrl: './data-grid.html'
})
export class DataGrid {
  readonly columns = input<GridColumn[]>([]);
  readonly rows = input<readonly unknown[]>([]);
  readonly title = input('');
  readonly loading = input(false);
  readonly searchable = input(true);
  readonly searchPlaceholder = input('Tìm kiếm…');
  readonly emptyText = input('Chưa có dữ liệu.');
  readonly pageSize = input(50);
  readonly pageSizes = input<number[]>([50, 100, 200]);
  readonly expandable = input(false);
  /** Hiện nút "Xuất Excel" (mặc định bật cho MỌI lưới). */
  readonly exportable = input(true);
  /** Tên file khi xuất (không kèm .xlsx); rỗng → dùng title/tiêu đề mặc định. */
  readonly exportName = input('');

  private readonly cellDirs = contentChildren(GridCellDirective);
  private readonly detailDir = contentChild(GridDetailDirective);
  readonly detailTpl = computed(() => this.detailDir()?.tpl ?? null);
  readonly cellTpl = computed(() => {
    const map = new Map<string, TemplateRef<unknown>>();
    for (const d of this.cellDirs()) {
      map.set(d.columnKey(), d.tpl);
    }
    return map;
  });

  readonly query = signal('');
  readonly sortKey = signal<string | null>(null);
  readonly sortDir = signal<SortDir>(null);
  readonly page = signal(1);
  readonly pageSizeOverride = signal<number | null>(null);
  readonly effPageSize = computed(() => this.pageSizeOverride() ?? this.pageSize());
  private readonly expandedKeys = signal<Set<number>>(new Set());

  readonly colspan = computed(() => this.columns().length + (this.expandable() ? 1 : 0));

  private readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const rows = this.rows();
    if (!q) {
      return rows;
    }
    const keys = this.columns().map((c) => c.key);
    return rows.filter((r) =>
      keys.some((k) => String((r as Record<string, unknown>)[k] ?? '').toLowerCase().includes(q))
    );
  });

  private readonly sorted = computed(() => {
    const key = this.sortKey();
    const dir = this.sortDir();
    const rows = [...this.filtered()];
    if (!key || !dir) {
      return rows;
    }
    const sign = dir === 'asc' ? 1 : -1;
    return rows.sort((a, b) => {
      const av = (a as Record<string, unknown>)[key];
      const bv = (b as Record<string, unknown>)[key];
      if (av == null) return 1;
      if (bv == null) return -1;
      if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * sign;
      return String(av).localeCompare(String(bv), 'vi') * sign;
    });
  });

  readonly total = computed(() => this.sorted().length);
  readonly pageCount = computed(() => Math.max(1, Math.ceil(this.total() / this.effPageSize())));
  readonly pageRows = computed(() => {
    const p = Math.min(this.page(), this.pageCount());
    const start = (p - 1) * this.effPageSize();
    return this.sorted().slice(start, start + this.effPageSize());
  });
  readonly rangeFrom = computed(() => (this.total() === 0 ? 0 : (Math.min(this.page(), this.pageCount()) - 1) * this.effPageSize() + 1));
  readonly rangeTo = computed(() => Math.min(this.page() * this.effPageSize(), this.total()));
  /** Danh sách số trang để render nút (cửa sổ quanh trang hiện tại). */
  readonly pageNumbers = computed(() => {
    const count = this.pageCount();
    const cur = Math.min(this.page(), count);
    const out: number[] = [];
    const push = (n: number) => { if (n >= 1 && n <= count && !out.includes(n)) out.push(n); };
    push(1);
    for (let i = cur - 1; i <= cur + 1; i++) push(i);
    push(count);
    return out;
  });

  onSearch(value: string): void {
    this.query.set(value);
    this.page.set(1);
  }

  setPageSize(value: string): void {
    this.pageSizeOverride.set(Number(value));
    this.page.set(1);
  }

  goTo(p: number): void {
    this.page.set(Math.min(Math.max(1, p), this.pageCount()));
  }

  toggleSort(col: GridColumn): void {
    if (!col.sortable) {
      return;
    }
    if (this.sortKey() !== col.key) {
      this.sortKey.set(col.key);
      this.sortDir.set('asc');
    } else {
      this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : this.sortDir() === 'desc' ? null : 'asc');
      if (this.sortDir() === null) {
        this.sortKey.set(null);
      }
    }
    this.page.set(1);
  }

  sortIndicator(col: GridColumn): string {
    if (this.sortKey() !== col.key) return '';
    return this.sortDir() === 'asc' ? '▲' : this.sortDir() === 'desc' ? '▼' : '';
  }

  isExpanded(i: number): boolean {
    return this.expandedKeys().has(this.absoluteIndex(i));
  }
  toggleExpand(i: number): void {
    const key = this.absoluteIndex(i);
    const next = new Set(this.expandedKeys());
    next.has(key) ? next.delete(key) : next.add(key);
    this.expandedKeys.set(next);
  }
  private absoluteIndex(i: number): number {
    return (Math.min(this.page(), this.pageCount()) - 1) * this.effPageSize() + i;
  }

  prev(): void { this.page.update((p) => Math.max(1, p - 1)); }
  next(): void { this.page.update((p) => Math.min(this.pageCount(), p + 1)); }

  cellValue(row: unknown, key: string): unknown {
    return (row as Record<string, unknown>)[key];
  }

  readonly exporting = signal(false);

  /** Cột được xuất: bỏ cột không có tiêu đề (thường là cột nút thao tác). */
  private exportColumns(): GridColumn[] {
    return this.columns().filter((c) => (c.header ?? '').trim().length > 0);
  }

  /** Giá trị ô để ghi Excel: giữ số, gộp mảng, JSON cho object, rỗng nếu trống. */
  private exportCell(row: unknown, key: string): string | number {
    const v = (row as Record<string, unknown>)[key];
    if (v == null) return '';
    if (typeof v === 'number' || typeof v === 'string') return v;
    if (typeof v === 'boolean') return v ? 'Có' : 'Không';
    if (Array.isArray(v)) return v.join(', ');
    try { return JSON.stringify(v); } catch { return String(v); }
  }

  /** Xuất TOÀN BỘ dòng đang lọc (mọi trang, theo thứ tự đang sắp) ra file .xlsx. */
  async exportExcel(): Promise<void> {
    if (this.exporting() || this.total() === 0) return;
    this.exporting.set(true);
    try {
      const cols = this.exportColumns();
      const rows = this.sorted();
      const aoa: (string | number)[][] = [
        cols.map((c) => c.header),
        ...rows.map((r) => cols.map((c) => this.exportCell(r, c.key)))
      ];
      const mod: any = await import('xlsx');
      const XLSX = mod?.utils ? mod : (mod?.default ?? mod);
      const ws = XLSX.utils.aoa_to_sheet(aoa);
      // Độ rộng cột theo nội dung (giới hạn 10..60 ký tự) cho dễ đọc.
      ws['!cols'] = cols.map((c, i) => {
        const maxLen = Math.max(c.header.length, ...aoa.slice(1).map((row) => String(row[i] ?? '').length));
        return { wch: Math.min(60, Math.max(10, maxLen + 2)) };
      });
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, this.sheetName());
      // Xuất qua Blob + <a download> (đáng tin cậy hơn XLSX.writeFile trong môi trường bundler).
      const buf: ArrayBuffer = XLSX.write(wb, { type: 'array', bookType: 'xlsx' });
      const blob = new Blob([buf], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = this.fileName();
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1500);
    } catch (e) {
      console.error('Xuất Excel lỗi:', e);
    } finally {
      this.exporting.set(false);
    }
  }

  private sheetName(): string {
    const base = (this.exportName() || this.title() || 'Dữ liệu').replace(/[\\/*?:\[\]]/g, ' ').trim();
    return (base || 'Dữ liệu').slice(0, 31);
  }

  private fileName(): string {
    const base = (this.exportName() || this.title() || 'du-lieu')
      .toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd')
      .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'du-lieu';
    const d = new Date();
    const p = (n: number) => String(n).padStart(2, '0');
    const stamp = `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}_${p(d.getHours())}${p(d.getMinutes())}`;
    return `${base}_${stamp}.xlsx`;
  }
}
