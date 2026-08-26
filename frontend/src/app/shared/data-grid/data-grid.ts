import { Component, ElementRef, computed, contentChild, contentChildren, inject, input, signal, TemplateRef } from '@angular/core';
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
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly columns = input<GridColumn[]>([]);
  readonly rows = input<readonly unknown[]>([]);

  /**
   * Cột CO GIÃN = cột đầu tiên không khai báo width — theo quy ước đó luôn là cột nội dung
   * chính (Tiêu đề, Tên dự án, Thành viên…).
   */
  private readonly flexKey = computed<string | null>(() =>
    this.columns().find((c) => !c.width)?.key ?? null);

  /**
   * Bề rộng cột. Bảng chạy `table-layout: fixed` nên bề rộng khai báo được tôn trọng nguyên vẹn;
   * cột co giãn cố tình BỎ TRỐNG để nhận đúng phần chỗ còn lại.
   *
   * Trước đây cột co giãn được gán 100% cho hợp với auto-layout, nhưng khi đó một ô nowrap
   * (tiêu đề công việc dài) vẫn đẩy bảng rộng ra và ép các cột khác xuống dưới bề rộng khai báo.
   */
  widthOf(col: GridColumn): string | null {
    return col.width ?? null;
  }

  /**
   * Bề rộng tối thiểu của cả bảng — đặt cho lưới NHIỀU cột (vd xem trước import nhân sự): tổng bề
   * rộng cột vượt màn thì cuộn ngang có chủ đích, còn hơn để fixed layout bóp đều mọi cột.
   */
  readonly minWidth = input('');
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
  /**
   * Ẩn HẲN thanh tiêu đề lưới (tên + đếm + tìm kiếm + nút xuất).
   *
   * Dùng khi màn đã có sẵn những thứ đó ở phía trên: số lượng nằm ở khối thống kê đầu trang,
   * ô tìm kiếm nằm trong thanh lọc chung. Giữ lại thanh này chỉ tổ lặp thông tin và ăn mất
   * một hàng màn hình. Nhớ tự đặt nút xuất Excel ở ngoài và gọi {@link exportExcel}.
   */
  readonly toolbar = input(true);
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

  /** Khoá track hàng: ưu tiên id ổn định (tránh tái dùng DOM/ô select sai khi list đổi/lọc); fallback vị trí. */
  rowKey(row: any, i: number): unknown { return row && row.id != null ? row.id : i; }

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

  /** Giá trị thô nếu là kiểu nguyên thủy (dùng trực tiếp); null nếu là object/không có (sẽ đọc từ ô hiển thị). */
  private rawPrimitive(row: unknown, key: string): string | number | null {
    const v = (row as Record<string, unknown>)[key];
    if (v == null) return null;
    if (typeof v === 'number' || typeof v === 'string') return v;
    if (typeof v === 'boolean') return v ? 'Có' : 'Không';
    return null;
  }

  /** Chuỗi số thuần → số (để Excel tính được); còn lại giữ nguyên chuỗi. */
  private coerceNum(s: string): string | number {
    return /^-?\d+(\.\d+)?$/.test(s) ? Number(s) : s;
  }

  /**
   * Xuất TOÀN BỘ dòng đang lọc (mọi trang, theo thứ tự đang sắp) ra .xlsx.
   * Ưu tiên dữ liệu thô theo key; cột render bằng template (không có key) → đọc TEXT ô đã hiển thị
   * (tạm render hết dòng để đọc), nên mọi lưới đều xuất được nội dung.
   */
  async exportExcel(): Promise<void> {
    if (this.exporting() || this.total() === 0) return;
    this.exporting.set(true);
    const prevOverride = this.pageSizeOverride();
    const prevPage = this.page();
    try {
      const cols = this.exportColumns();
      const allCols = this.columns();
      const rows = this.sorted();
      // Tạm render TẤT CẢ dòng để đọc được text các ô dùng template.
      this.pageSizeOverride.set(Math.max(1, rows.length));
      this.page.set(1);
      await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(() => r(null))));
      const trs = Array.from(
        this.host.nativeElement.querySelectorAll('.grid__scroll tbody > tr:not(.grid__detail-row)')
      ) as HTMLElement[];
      const off = this.expandable() ? 1 : 0;
      const aoa: (string | number)[][] = [cols.map((c) => c.header)];
      rows.forEach((r, ri) => {
        const tds = trs[ri] ? (Array.from(trs[ri].children) as HTMLElement[]) : [];
        aoa.push(cols.map((c) => {
          // Ưu tiên ĐÚNG NHƯ HIỂN THỊ (nhãn, ô ghép, số đã định dạng); rỗng thì mới lấy dữ liệu thô.
          const td = tds[off + allCols.indexOf(c)];
          const txt = (td?.textContent ?? '').replace(/\s+/g, ' ').trim();
          if (txt !== '') return this.coerceNum(txt);
          const raw = this.rawPrimitive(r, c.key);
          return raw !== null ? raw : '';
        }));
      });
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
      this.pageSizeOverride.set(prevOverride);
      this.page.set(prevPage);
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
