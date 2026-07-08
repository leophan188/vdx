import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CdkDropList, CdkDrag, CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { FormService } from '../../core/form.service';
import { ToastService } from '../../shared/toast/toast.service';
import { Modal } from '../../shared/modal/modal';
import { OrgTreePicker } from '../../shared/org-tree-picker/org-tree-picker';
import { UnitStaffPicker } from '../../shared/unit-staff-picker/unit-staff-picker';

type FieldType = 'text' | 'textarea' | 'number' | 'date' | 'datetime' | 'boolean'
  | 'dropdown' | 'radio' | 'multiselect' | 'file' | 'richtext' | 'table' | 'scoretable'
  | 'orgtree' | 'unitstaff' | 'section';

/** Kiểu dữ liệu của một cột trong bảng nhiều dòng — đầy đủ như trường thường (trừ loại bố cục). */
type ColumnType = 'text' | 'textarea' | 'number' | 'date' | 'datetime' | 'boolean'
  | 'dropdown' | 'multiselect' | 'orgtree' | 'unitstaff';
interface FormColumn { key: string; label: string; type?: ColumnType; options?: string; pickMode?: 'user' | 'position' | 'unit'; }
/** Một tiêu chí của bảng chấm điểm: nhãn + trọng số (%). */
interface FormCriterion { key: string; label: string; weight: number; }
type CondOp = 'eq' | 'ne' | 'truthy';
type FormatRule = 'none' | 'email' | 'phone';
interface VisibleWhen { field: string; op: CondOp; value?: string; }
interface Validation { min?: number | null; max?: number | null; maxLength?: number | null; format?: FormatRule; }
interface FormField {
  id: string;
  key: string;
  label: string;
  type: FieldType;
  required?: boolean;
  placeholder?: string;
  /** Giá trị điền sẵn khi mở biểu mẫu (nếu người dùng chưa nhập). */
  defaultValue?: string;
  optionSource?: 'STATIC' | 'CATALOG';
  options?: string;
  catalog?: string;
  columns?: FormColumn[];
  criteria?: FormCriterion[];
  scoreMax?: number;
  visibleWhen?: VisibleWhen;
  validation?: Validation;
  pickMode?: 'user' | 'position' | 'unit'; // cho type orgtree
}

/** Form builder kéo-thả (Story 2.6): palette loại trường + canvas reorder + thuộc tính + xem trước. */
@Component({
  selector: 'app-builder',
  imports: [FormsModule, CdkDropList, CdkDrag, Modal, OrgTreePicker, UnitStaffPicker],
  templateUrl: './builder.html'
})
export class Builder implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private svc = inject(FormService);
  private toast = inject(ToastService);

  readonly id = this.route.snapshot.paramMap.get('id')!;
  readonly name = signal('');
  readonly saving = signal(false);
  readonly previewOpen = signal(false);

  readonly fields = signal<FormField[]>([]);
  readonly selectedIdx = signal<number | null>(null);
  readonly selected = computed(() => {
    const i = this.selectedIdx();
    return i != null ? this.fields()[i] ?? null : null;
  });

  private seq = 0;

  readonly PALETTE: { t: FieldType; l: string; i: string }[] = [
    { t: 'text', l: 'Văn bản', i: '🅣' },
    { t: 'textarea', l: 'Văn bản nhiều dòng', i: '☰' },
    { t: 'number', l: 'Số', i: '#' },
    { t: 'date', l: 'Ngày', i: '📆' },
    { t: 'datetime', l: 'Ngày - giờ', i: '📅' },
    { t: 'boolean', l: 'Có / Không', i: '☑' },
    { t: 'dropdown', l: 'Danh sách chọn', i: '▾' },
    { t: 'radio', l: 'Chọn một (radio)', i: '◉' },
    { t: 'multiselect', l: 'Chọn nhiều', i: '☑☑' },
    { t: 'file', l: 'Tải file', i: '📎' },
    { t: 'richtext', l: 'Rich text', i: '📝' },
    { t: 'table', l: 'Bảng nhiều dòng', i: '▦' },
    { t: 'scoretable', l: 'Bảng chấm điểm', i: '🧮' },
    { t: 'orgtree', l: 'Chọn theo cây tổ chức', i: '🌳' },
    { t: 'unitstaff', l: 'Cây đơn vị → chọn nhân sự', i: '👥' },
    { t: 'section', l: 'Tiêu đề mục', i: '§' }
  ];

  readonly FIELD_TYPES = this.PALETTE.map((p) => ({ v: p.t, l: p.l }));
  /** Kiểu dữ liệu chọn được cho từng cột của bảng nhiều dòng. */
  readonly COLUMN_TYPES: { v: ColumnType; l: string }[] = [
    { v: 'text', l: 'Văn bản' },
    { v: 'textarea', l: 'Văn bản nhiều dòng' },
    { v: 'number', l: 'Số' },
    { v: 'date', l: 'Ngày' },
    { v: 'datetime', l: 'Ngày - giờ' },
    { v: 'boolean', l: 'Có / Không' },
    { v: 'dropdown', l: 'Danh sách chọn' },
    { v: 'multiselect', l: 'Chọn nhiều' },
    { v: 'orgtree', l: 'Chọn theo cây tổ chức' },
    { v: 'unitstaff', l: 'Cây đơn vị → chọn nhân sự' }
  ];
  readonly COND_OPS: { v: CondOp; l: string }[] = [
    { v: 'truthy', l: 'có giá trị' },
    { v: 'eq', l: 'bằng' },
    { v: 'ne', l: 'khác' }
  ];
  readonly FORMATS: { v: FormatRule; l: string }[] = [
    { v: 'none', l: 'Không' },
    { v: 'email', l: 'Email' },
    { v: 'phone', l: 'Số điện thoại' }
  ];

  // ---- Trạng thái xem trước (tương tác để demo ẩn/hiện + validation) ----
  readonly values = signal<Record<string, unknown>>({});
  readonly submitted = signal(false);

  ngOnInit(): void {
    this.svc.get(this.id).subscribe({
      next: (f) => {
        this.name.set(f.name);
        if (f.schemaJson) {
          try {
            const parsed = JSON.parse(f.schemaJson);
            const fs: FormField[] = (parsed.fields ?? []).map((x: FormField) => ({ ...x, id: x.id ?? this.nextId() }));
            this.fields.set(fs);
          } catch {
            /* schema lỗi → form rỗng */
          }
        }
      },
      error: () => this.toast.error('Không tải được biểu mẫu')
    });
  }

  private nextId(): string {
    return 'f' + ++this.seq + '_' + Math.floor(performance.now());
  }

  typeLabel(t: FieldType): string {
    return this.PALETTE.find((p) => p.t === t)?.l ?? t;
  }
  isChoice(t?: FieldType): boolean { return t === 'dropdown' || t === 'radio' || t === 'multiselect'; }
  isTable(t?: FieldType): boolean { return t === 'table'; }
  isScoreTable(t?: FieldType): boolean { return t === 'scoretable'; }
  isSection(t?: FieldType): boolean { return t === 'section'; }

  addField(type: FieldType): void {
    const n = this.fields().length + 1;
    const f: FormField = {
      id: this.nextId(),
      key: 'truong_' + n,
      label: this.typeLabel(type) + ' ' + n,
      type,
      optionSource: 'STATIC',
      options: '',
      columns: type === 'table' ? [{ key: 'cot1', label: 'Cột 1', type: 'text' as ColumnType }] : undefined,
      criteria: type === 'scoretable' ? [{ key: 'tc1', label: 'Tiêu chí 1', weight: 100 }] : undefined,
      scoreMax: type === 'scoretable' ? 10 : undefined
    };
    this.fields.update((arr) => [...arr, f]);
    this.selectedIdx.set(this.fields().length - 1);
  }

  // ---- Bảng chấm điểm: tiêu chí + trọng số ----
  addCriterion(f: FormField): void {
    const n = (f.criteria?.length ?? 0) + 1;
    f.criteria = [...(f.criteria ?? []), { key: 'tc' + n, label: 'Tiêu chí ' + n, weight: 0 }];
    this.touch();
  }
  removeCriterion(f: FormField, i: number): void {
    f.criteria = (f.criteria ?? []).filter((_, idx) => idx !== i);
    this.touch();
  }
  totalWeight(f: FormField): number {
    return (f.criteria ?? []).reduce((s, c) => s + (Number(c.weight) || 0), 0);
  }

  selectField(i: number): void { this.selectedIdx.set(i); }

  removeField(i: number): void {
    this.fields.update((arr) => arr.filter((_, idx) => idx !== i));
    this.selectedIdx.set(null);
  }

  drop(e: CdkDragDrop<FormField[]>): void {
    const arr = [...this.fields()];
    moveItemInArray(arr, e.previousIndex, e.currentIndex);
    this.fields.set(arr);
    this.selectedIdx.set(e.currentIndex);
  }

  // table columns
  addColumn(f: FormField): void {
    f.columns = [...(f.columns ?? []), { key: 'cot' + ((f.columns?.length ?? 0) + 1), label: 'Cột mới', type: 'text' }];
    this.touch();
  }
  /** Lựa chọn của cột kiểu dropdown (chuỗi ngăn cách dấu phẩy). */
  colOptions(c: FormColumn): string[] {
    return (c.options ?? '').split(',').map((s) => s.trim()).filter(Boolean);
  }
  /** Loại trường có hỗ trợ đặt "giá trị mặc định". */
  supportsDefault(t?: FieldType): boolean {
    return ['text', 'textarea', 'number', 'date', 'datetime', 'boolean', 'dropdown', 'radio', 'multiselect'].includes(t ?? '');
  }
  isSingleChoice(t?: FieldType): boolean { return t === 'dropdown' || t === 'radio'; }
  /** Input type HTML cho ô nhập giá trị mặc định theo loại trường. */
  defaultInputType(t?: FieldType): string {
    return t === 'number' ? 'number' : t === 'date' ? 'date' : t === 'datetime' ? 'datetime-local' : 'text';
  }
  removeColumn(f: FormField, i: number): void {
    f.columns = (f.columns ?? []).filter((_, idx) => idx !== i);
    this.touch();
  }
  /** ép signal phát lại để view cập nhật khi sửa thuộc tính trong object. */
  touch(): void { this.fields.set([...this.fields()]); }

  optionList(f: FormField): string[] {
    return (f.options ?? '').split(',').map((s) => s.trim()).filter(Boolean);
  }

  // ---- Điều kiện hiển thị (Story 2.8) ----
  otherFields(f: FormField): { key: string; label: string }[] {
    return this.fields().filter((x) => x.key && x.key !== f.key).map((x) => ({ key: x.key, label: x.label || x.key }));
  }
  hasCondition(f: FormField): boolean { return !!f.visibleWhen; }
  toggleCondition(f: FormField, on: boolean): void {
    f.visibleWhen = on ? { field: '', op: 'truthy' } : undefined;
    this.touch();
  }
  ensureValidation(f: FormField): Validation {
    if (!f.validation) f.validation = { format: 'none' };
    return f.validation;
  }

  // ---- Đánh giá ẩn/hiện + validation cho xem trước ----
  isVisible(f: FormField): boolean {
    const c = f.visibleWhen;
    if (!c || !c.field) return true;
    const v = this.values()[c.field];
    if (c.op === 'truthy') return v !== undefined && v !== null && v !== '' && v !== false;
    const sv = v == null ? '' : String(v);
    return c.op === 'eq' ? sv === (c.value ?? '') : sv !== (c.value ?? '');
  }
  setVal(key: string, v: unknown): void {
    this.values.update((o) => ({ ...o, [key]: v }));
  }
  /** Multiselect: lưu chuỗi ngăn cách dấu phẩy. */
  hasMulti(key: string, opt: string): boolean {
    return String(this.values()[key] ?? '').split(',').map((s) => s.trim()).includes(opt);
  }
  toggleMulti(key: string, opt: string): void {
    const cur = String(this.values()[key] ?? '').split(',').map((s) => s.trim()).filter(Boolean);
    const i = cur.indexOf(opt);
    if (i >= 0) cur.splice(i, 1); else cur.push(opt);
    this.setVal(key, cur.join(', '));
  }
  private errorFor(f: FormField): string | null {
    if (!this.isVisible(f)) return null; // ẩn → bỏ qua validate
    const v = this.values()[f.key];
    const empty = v === undefined || v === null || v === '' || v === false;
    if (f.required && empty) return 'Bắt buộc';
    const val = f.validation;
    if (!val || empty) return null;
    if (f.type === 'number') {
      const n = Number(v);
      if (val.min != null && n < val.min) return 'Tối thiểu ' + val.min;
      if (val.max != null && n > val.max) return 'Tối đa ' + val.max;
    }
    if (f.type === 'text' || f.type === 'richtext') {
      const s = String(v);
      if (val.maxLength != null && s.length > val.maxLength) return 'Tối đa ' + val.maxLength + ' ký tự';
      if (val.format === 'email' && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(s)) return 'Email không hợp lệ';
      if (val.format === 'phone' && !/^[0-9+\-\s]{6,}$/.test(s)) return 'Số điện thoại không hợp lệ';
    }
    return null;
  }
  shownError(f: FormField): string | null {
    return this.submitted() ? this.errorFor(f) : null;
  }
  openPreview(): void {
    this.values.set(this.initialDefaults());
    this.submitted.set(false);
    this.previewOpen.set(true);
  }
  /** Giá trị điền sẵn từ "defaultValue" của các trường (dùng khi mở xem trước / mở việc). */
  private initialDefaults(): Record<string, unknown> {
    const vals: Record<string, unknown> = {};
    for (const f of this.fields()) {
      if (this.supportsDefault(f.type) && f.defaultValue != null && f.defaultValue !== '') {
        vals[f.key] = f.type === 'boolean' ? (f.defaultValue === 'true' || f.defaultValue === '1') : f.defaultValue;
      }
    }
    return vals;
  }
  validateAll(): void {
    this.submitted.set(true);
    const bad = this.fields().some((f) => this.errorFor(f));
    if (bad) {
      this.toast.error('Có lỗi nhập liệu', 'Vui lòng kiểm tra các trường tô đỏ.');
    } else {
      this.toast.success('Dữ liệu hợp lệ', 'Biểu mẫu sẵn sàng gửi.');
    }
  }

  async save(): Promise<void> {
    this.saving.set(true);
    const clean = this.fields().filter((f) => f.key.trim());
    this.svc.saveSchema(this.id, JSON.stringify({ fields: clean })).subscribe({
      next: () => { this.toast.success('Đã lưu biểu mẫu', this.name()); this.saving.set(false); },
      error: () => { this.toast.error('Không lưu được biểu mẫu'); this.saving.set(false); }
    });
  }

  back(): void { this.router.navigate(['/forms']); }
}
