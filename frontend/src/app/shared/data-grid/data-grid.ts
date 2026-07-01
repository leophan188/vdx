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
}
