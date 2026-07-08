import { Component, computed, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Modal } from '../modal/modal';
import { OrgTreePicker } from '../org-tree-picker/org-tree-picker';
import { UnitStaffPicker } from '../unit-staff-picker/unit-staff-picker';
import { ToastService } from '../toast/toast.service';
import { WorkflowService, TaskDetail, StepView } from '../../core/workflow.service';
import { FormService, AttachmentRef } from '../../core/form.service';

type Perm = 'EDIT' | 'READONLY' | 'HIDDEN';
interface RCriterion { key: string; label: string; weight: number; }
interface RColumn { key: string; label: string; type?: string; options?: string; pickMode?: 'user' | 'position' | 'unit'; }
interface RField {
  key: string; label: string; type: string; required?: boolean;
  placeholder?: string; options?: string; defaultValue?: string;
  criteria?: RCriterion[]; scoreMax?: number;
  pickMode?: 'user' | 'position' | 'unit'; columns?: RColumn[];
}

/**
 * Modal xử lý một việc (Story 3.4) dùng chung: render biểu mẫu gắn bước theo quyền trường + nút hành động.
 * Hiển thị NỘI DUNG YÊU CẦU (bước đầu) + DỮ LIỆU TỪ BƯỚC TRƯỚC (accordion từng bước, gập mặc định).
 * Mở bằng openTask(taskId); phát (completed) khi hoàn thành để màn cha tải lại.
 */
@Component({
  selector: 'app-task-processor',
  imports: [FormsModule, Modal, OrgTreePicker, UnitStaffPicker],
  templateUrl: './task-processor.html',
  styles: [`
    .tp-card{border:1px solid var(--color-border);border-radius:10px;padding:12px 14px;margin-bottom:14px;background:var(--color-surface-2,rgba(127,127,127,.05));}
    .tp-sechead{display:flex;align-items:center;gap:8px;font-weight:700;font-size:.8em;letter-spacing:.04em;text-transform:uppercase;margin:0 0 10px;}
    .tp-sechead--sep{margin-top:6px;padding-top:2px;}
    .tp-chip{display:inline-flex;align-items:center;gap:4px;background:var(--color-primary-soft,rgba(30,80,160,.12));color:var(--color-primary,#1e50a0);border-radius:999px;padding:2px 10px;font-size:.85em;font-weight:600;text-transform:none;letter-spacing:0;}
    .tp-badge-done{margin-left:auto;background:var(--status-done,#16a34a);color:#fff;border-radius:999px;padding:2px 10px;font-size:.82em;font-weight:600;text-transform:none;letter-spacing:0;}
    /* Lưới nhiều cột — tự co theo bề rộng; ô dài (.tp-full) chiếm cả hàng. */
    .tp-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:10px 22px;align-items:start;}
    .tp-full{grid-column:1/-1;}
    .tp-reqfields{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:10px 22px;}
    .tp-fld > label{display:block;font-size:.74em;text-transform:uppercase;letter-spacing:.04em;opacity:.6;margin-bottom:3px;font-weight:700;}
    .tp-ro{border:1px solid var(--color-border);border-radius:8px;padding:8px 10px;background:var(--color-surface);color:var(--color-text);white-space:pre-wrap;}
    .tp-accordion{display:flex;flex-direction:column;gap:8px;margin-bottom:16px;}
    .tp-step{border:1px solid var(--color-border);border-radius:10px;overflow:hidden;}
    .tp-step-head{width:100%;display:flex;align-items:center;gap:10px;padding:10px 12px;background:transparent;border:0;cursor:pointer;color:inherit;text-align:left;font:inherit;}
    .tp-num{flex:0 0 24px;height:24px;border-radius:50%;background:var(--status-done,#16a34a);color:#fff;display:inline-flex;align-items:center;justify-content:center;font-size:.8em;font-weight:700;}
    .tp-step-title{flex:1;min-width:0;font-weight:600;}
    .tp-step-meta{display:flex;gap:8px;align-items:center;font-size:.82em;opacity:.7;white-space:nowrap;}
    .tp-step-body{padding:2px 14px 14px 46px;display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:10px 22px;}
    .tp-caret{opacity:.5;}
    /* Ô nhập của form bước hiện tại co giãn theo cột lưới. */
    #task-proc-form .field > input, #task-proc-form .field > select, #task-proc-form .field > textarea{width:100%;box-sizing:border-box;}
    /* Bảng chấm điểm */
    .tp-scoretable{width:100%;border-collapse:collapse;font-size:.94em;}
    .tp-scoretable th,.tp-scoretable td{border:1px solid var(--color-border);padding:6px 10px;text-align:left;}
    .tp-scoretable thead th{background:var(--color-surface-2,rgba(127,127,127,.06));font-size:.86em;text-transform:uppercase;letter-spacing:.02em;}
    .tp-scoretable .tp-st-c{text-align:center;}
    .tp-scoretable .tp-st-c > input{width:72px;text-align:center;box-sizing:border-box;}
    .tp-scoretable .tp-st-conv{font-weight:600;color:var(--color-primary,#1e50a0);}
    .tp-scoretable tfoot th{background:var(--color-surface-2,rgba(127,127,127,.06));}
    .tp-scoretable .tp-st-total{text-align:center;font-weight:700;color:var(--status-done,#16a34a);font-size:1.05em;}
    /* Bảng nhiều dòng */
    .tp-mtable{width:100%;border-collapse:collapse;font-size:.92em;margin-bottom:6px;}
    .tp-mtable th,.tp-mtable td{border:1px solid var(--color-border);padding:4px 8px;text-align:left;}
    .tp-mtable thead th{background:var(--color-surface-2,rgba(127,127,127,.06));font-size:.86em;}
    .tp-mtable td > input{width:100%;box-sizing:border-box;border:0;background:transparent;color:inherit;font:inherit;padding:2px 0;}
    .tp-mtable-empty{text-align:center;opacity:.6;}
    /* Danh sách tệp đính kèm */
    .tp-filelist{list-style:none;margin:8px 0 0;padding:0;display:flex;flex-direction:column;gap:6px;}
    .tp-fileitem{display:flex;align-items:center;gap:10px;border:1px solid var(--color-border);border-radius:8px;padding:6px 10px;background:var(--color-surface);}
    .tp-filename{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--color-primary,#1e50a0);text-decoration:none;}
    .tp-filename:hover{text-decoration:underline;}
    .tp-filesize{font-size:.82em;opacity:.6;white-space:nowrap;}
  `]
})
export class TaskProcessor {
  private wf = inject(WorkflowService);
  private formSvc = inject(FormService);
  private toast = inject(ToastService);

  /** Nhãn tiếng Việt của mã hành động (đồng bộ với ALL_ACTIONS bên designer). */
  private static readonly ACTION_LABELS: Record<string, string> = {
    RECORD: 'Ghi lại', EDIT: 'Sửa', CANCEL: 'Hủy', SUBMIT: 'Trình duyệt',
    APPROVE: 'Phê duyệt', RETURN: 'Trả lại', REJECT: 'Từ chối', DELEGATE: 'Uỷ quyền'
  };
  /** Nhãn hiển thị của nút hành động: đổi mã (APPROVE…) sang tiếng Việt, giữ nguyên nếu đã là chữ thường. */
  actionLabel(code: string): string {
    return TaskProcessor.ACTION_LABELS[code] ?? code;
  }

  /** Phát khi việc đã hoàn thành (màn cha tải lại danh sách). */
  readonly completed = output<void>();

  readonly open = signal(false);
  readonly detail = signal<TaskDetail | null>(null);
  readonly fields = signal<RField[]>([]);
  /** Trường của bước trước được phép sửa ở bước này. */
  readonly priorFields = signal<RField[]>([]);
  readonly values = signal<Record<string, unknown>>({});
  readonly busy = signal(false);

  /** Danh sách render: [mục "Sửa bước trước" + trường bước trước] rồi [mục bước hiện tại + trường bước này]. */
  readonly renderFields = computed<RField[]>(() => {
    const pf = this.priorFields();
    const cur = this.fields();
    if (!pf.length) return cur;
    const out: RField[] = [{ key: '__sec_prior', label: '✏️ Chỉnh sửa thông tin bước trước', type: 'section' }, ...pf];
    if (cur.length) {
      out.push({ key: '__sec_cur', label: 'Nội dung bước hiện tại', type: 'section' }, ...cur);
    }
    return out;
  });

  /** Bước đầu (đề nghị) = "Nội dung yêu cầu"; các bước còn lại = accordion "Dữ liệu từ bước trước". */
  readonly requestStep = computed<StepView | null>(() => {
    const p = this.detail()?.priorSteps ?? [];
    return p.length ? p[0] : null;
  });
  readonly priorSteps = computed<StepView[]>(() => (this.detail()?.priorSteps ?? []).slice(1));

  /** Các bước trước đang mở (theo index). Mặc định gập hết — bấm mới mở. */
  readonly expandedPrior = signal<Set<number>>(new Set());
  isPriorOpen(index: number): boolean { return this.expandedPrior().has(index); }
  togglePrior(index: number): void {
    const next = new Set(this.expandedPrior());
    next.has(index) ? next.delete(index) : next.add(index);
    this.expandedPrior.set(next);
  }
  fmtDate(iso: string | null): string {
    if (!iso) return '';
    return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }

  /** Mở việc theo id: tải chi tiết + schema biểu mẫu. */
  openTask(taskId: string): void {
    this.busy.set(false);
    this.fields.set([]);
    this.priorFields.set([]);
    this.values.set({});
    this.detail.set(null);
    this.expandedPrior.set(new Set());
    this.open.set(true);
    this.wf.detail(taskId).subscribe({
      next: (d) => {
        this.detail.set(d);
        this.values.set({ ...d.formData });
        this.priorFields.set(d.priorEditFieldsJson ? this.parseFields(d.priorEditFieldsJson) : []);
        if (d.formSchemaJson) {
          // Ưu tiên schema ĐÓNG BĂNG theo phiên bản của phiên chạy → form không đổi dù cấu hình sửa sau này.
          this.setFieldsAndDefaults(d.formSchemaJson);
        } else if (d.formId) {
          this.formSvc.get(d.formId).subscribe({
            next: (f) => this.setFieldsAndDefaults(f.schemaJson),
            error: () => this.fields.set([])
          });
        } else {
          this.fields.set([]);
        }
      },
      error: () => { this.toast.error('Không mở được việc'); this.open.set(false); }
    });
  }

  close(): void { this.open.set(false); }

  /** Đặt danh sách trường + điền GIÁ TRỊ MẶC ĐỊNH cho ô còn trống (không đè dữ liệu đã có). */
  private setFieldsAndDefaults(schemaJson: string | null): void {
    const fs = this.parseFields(schemaJson);
    this.fields.set(fs);
    const cur = this.values();
    const patch: Record<string, unknown> = {};
    for (const f of fs) {
      const has = cur[f.key] !== undefined && cur[f.key] !== null && cur[f.key] !== '';
      if (!has && f.defaultValue != null && f.defaultValue !== '') {
        patch[f.key] = f.type === 'boolean' ? (f.defaultValue === 'true' || f.defaultValue === '1') : f.defaultValue;
      }
    }
    if (Object.keys(patch).length) this.values.update((o) => ({ ...o, ...patch }));
  }

  /** Lựa chọn của cột kiểu dropdown/multiselect trong bảng nhiều dòng. */
  colOpts(c: RColumn): string[] {
    return (c.options ?? '').split(',').map((s) => s.trim()).filter(Boolean);
  }
  /** Ô multiselect trong bảng: lưu chuỗi ngăn cách dấu phẩy ở cấp ô. */
  cellHasMulti(fieldKey: string, i: number, col: string, opt: string): boolean {
    return String(this.cellVal(fieldKey, i, col) ?? '').split(',').map((s) => s.trim()).includes(opt);
  }
  cellToggleMulti(fieldKey: string, i: number, col: string, opt: string): void {
    const cur = String(this.cellVal(fieldKey, i, col) ?? '').split(',').map((s) => s.trim()).filter(Boolean);
    const idx = cur.indexOf(opt);
    if (idx >= 0) cur.splice(idx, 1); else cur.push(opt);
    this.setCell(fieldKey, i, col, cur.join(', '));
  }

  private parseFields(schemaJson: string | null): RField[] {
    if (!schemaJson) return [];
    try {
      const parsed = JSON.parse(schemaJson);
      const arr = Array.isArray(parsed) ? parsed : (parsed.fields ?? []);
      return arr.map((f: Record<string, unknown>) => ({
        key: String(f['key'] ?? ''), label: String(f['label'] ?? f['key'] ?? ''),
        type: String(f['type'] ?? 'text'), required: !!f['required'],
        placeholder: f['placeholder'] as string, options: f['options'] as string,
        defaultValue: f['defaultValue'] != null ? String(f['defaultValue']) : undefined,
        criteria: Array.isArray(f['criteria'])
          ? (f['criteria'] as Record<string, unknown>[]).map((c) => ({
              key: String(c['key'] ?? ''), label: String(c['label'] ?? c['key'] ?? ''), weight: Number(c['weight']) || 0
            }))
          : undefined,
        scoreMax: f['scoreMax'] != null ? Number(f['scoreMax']) : undefined,
        pickMode: (f['pickMode'] as RField['pickMode']) ?? undefined,
        columns: Array.isArray(f['columns'])
          ? (f['columns'] as Record<string, unknown>[]).map((c) => ({
              key: String(c['key'] ?? ''), label: String(c['label'] ?? c['key'] ?? ''),
              type: c['type'] != null ? String(c['type']) : undefined,
              options: c['options'] != null ? String(c['options']) : undefined,
              pickMode: (c['pickMode'] as RColumn['pickMode']) ?? undefined
            }))
          : undefined
      })).filter((f: RField) => f.key);
    } catch { return []; }
  }

  perm(key: string): Perm {
    const p = this.detail()?.fieldPerms;
    return (p && p[key]) || 'EDIT';
  }
  visible(key: string): boolean { return this.perm(key) !== 'HIDDEN'; }
  readonly_(key: string): boolean { return this.perm(key) === 'READONLY'; }

  opts(f: RField): string[] {
    return (f.options ?? '').split(',').map((s) => s.trim()).filter(Boolean);
  }
  setVal(key: string, v: unknown): void { this.values.update((o) => ({ ...o, [key]: v })); }
  val(key: string): unknown { return this.values()[key]; }

  /** Multiselect: lưu chuỗi ngăn cách dấu phẩy. */
  hasMulti(key: string, opt: string): boolean {
    return String(this.val(key) ?? '').split(',').map((s) => s.trim()).includes(opt);
  }
  toggleMulti(key: string, opt: string): void {
    const cur = String(this.val(key) ?? '').split(',').map((s) => s.trim()).filter(Boolean);
    const i = cur.indexOf(opt);
    if (i >= 0) cur.splice(i, 1); else cur.push(opt);
    this.setVal(key, cur.join(', '));
  }

  // ----- Bảng chấm điểm (scoretable): lưu điểm dạng JSON {tiêuChíKey: điểm}, tự tính quy đổi + trung bình -----
  private tableScores(fieldKey: string): Record<string, number> {
    const raw = this.val(fieldKey);
    if (typeof raw === 'string' && raw.trim()) { try { return JSON.parse(raw); } catch { return {}; } }
    if (raw && typeof raw === 'object') return raw as Record<string, number>;
    return {};
  }
  scoreOf(fieldKey: string, critKey: string): number | '' {
    const s = this.tableScores(fieldKey)[critKey];
    return typeof s === 'number' ? s : '';
  }
  setScore(fieldKey: string, critKey: string, v: unknown): void {
    const cur = this.tableScores(fieldKey);
    const n = v === '' || v === null || v === undefined ? NaN : Number(v);
    if (isNaN(n)) delete cur[critKey]; else cur[critKey] = n;
    this.setVal(fieldKey, JSON.stringify(cur));
  }
  /** Điểm quy đổi của 1 tiêu chí = điểm × trọng số%. */
  convertedOf(score: number | '', weight: number): string {
    if (score === '' || isNaN(Number(score))) return '—';
    return (Number(score) * weight / 100).toFixed(2);
  }
  /** Điểm trung bình có trọng số (tổng quy đổi). */
  tableTotal(f: RField): string {
    const sc = this.tableScores(f.key);
    let sum = 0; let any = false;
    for (const c of f.criteria ?? []) {
      const s = sc[c.key];
      if (typeof s === 'number' && !isNaN(s)) { sum += s * c.weight / 100; any = true; }
    }
    return any ? sum.toFixed(2) : '—';
  }
  totalWeight(f: RField): number {
    return (f.criteria ?? []).reduce((a, c) => a + (Number(c.weight) || 0), 0);
  }

  // ----- Bảng nhiều dòng (table): lưu JSON [{col: value}]; thêm/xoá dòng, sửa ô -----
  tableData(fieldKey: string): Record<string, string>[] {
    const raw = this.val(fieldKey);
    if (typeof raw === 'string' && raw.trim()) { try { const a = JSON.parse(raw); return Array.isArray(a) ? a : []; } catch { return []; } }
    if (Array.isArray(raw)) return raw as Record<string, string>[];
    return [];
  }
  addRow(f: RField): void {
    const rows = this.tableData(f.key);
    const empty: Record<string, string> = {};
    for (const c of f.columns ?? []) empty[c.key] = '';
    this.setVal(f.key, JSON.stringify([...rows, empty]));
  }
  removeRow(fieldKey: string, i: number): void {
    const rows = this.tableData(fieldKey).filter((_, idx) => idx !== i);
    this.setVal(fieldKey, JSON.stringify(rows));
  }
  cellVal(fieldKey: string, i: number, col: string): string {
    return this.tableData(fieldKey)[i]?.[col] ?? '';
  }
  setCell(fieldKey: string, i: number, col: string, v: unknown): void {
    const rows = this.tableData(fieldKey);
    if (!rows[i]) return;
    rows[i] = { ...rows[i], [col]: String(v ?? '') };
    this.setVal(fieldKey, JSON.stringify(rows));
  }

  // ----- Trường "Tải file": giá trị lưu JSON mảng [{id,name,size,contentType,url}] -----
  /** Các key trường đang upload dở (hiện trạng thái "đang tải"). */
  readonly uploading = signal<Set<string>>(new Set());
  isUploading(key: string): boolean { return this.uploading().has(key); }

  fileList(key: string): AttachmentRef[] {
    const raw = this.val(key);
    if (typeof raw === 'string' && raw.trim()) { try { const a = JSON.parse(raw); return Array.isArray(a) ? a : []; } catch { return []; } }
    if (Array.isArray(raw)) return raw as AttachmentRef[];
    return [];
  }
  /** Upload lần lượt các file được chọn rồi gộp vào danh sách của trường. */
  onPickFiles(key: string, ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    if (!files.length) return;
    const mark = new Set(this.uploading()); mark.add(key); this.uploading.set(mark);
    let remaining = files.length;
    const done = () => {
      remaining--;
      if (remaining <= 0) { const s = new Set(this.uploading()); s.delete(key); this.uploading.set(s); }
    };
    for (const file of files) {
      this.formSvc.uploadAttachment(file).subscribe({
        next: (ref) => { this.setVal(key, JSON.stringify([...this.fileList(key), ref])); done(); },
        error: (e) => { this.toast.error('Không tải được tệp', e?.error?.message || file.name); done(); }
      });
    }
    input.value = ''; // cho phép chọn lại cùng file
  }
  removeFile(key: string, id: string): void {
    this.setVal(key, JSON.stringify(this.fileList(key).filter((a) => a.id !== id)));
  }
  fileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB';
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  }

  /** Một trường đã có dữ liệu (dùng cho kiểm tra bắt buộc). Bảng chấm điểm: mọi tiêu chí phải có điểm. */
  private isFilled(f: RField): boolean {
    if (f.type === 'file') return this.fileList(f.key).length > 0;
    if (f.type === 'scoretable') {
      const sc = this.tableScores(f.key);
      return (f.criteria ?? []).length > 0 && (f.criteria ?? []).every((c) => typeof sc[c.key] === 'number' && !isNaN(sc[c.key]));
    }
    if (f.type === 'table') return this.tableData(f.key).length > 0;
    const v = this.val(f.key);
    return !(v === undefined || v === null || v === '');
  }

  /** Hoàn thành việc với hành động đã chọn. */
  act(action: string): void {
    const d = this.detail();
    if (!d) return;
    const missing = this.fields().filter((f) =>
      f.required && this.visible(f.key) && !this.readonly_(f.key) && !this.isFilled(f));
    if (missing.length) {
      this.toast.error('Thiếu thông tin bắt buộc', missing.map((f) => f.label).join(', '));
      return;
    }
    this.busy.set(true);
    this.wf.complete(d.taskId, action, this.values()).subscribe({
      next: () => {
        this.toast.success('Đã xử lý việc', `${d.stepName} • ${action}`);
        this.open.set(false);
        this.completed.emit();
      },
      error: () => { this.toast.error('Không hoàn thành được việc'); this.busy.set(false); }
    });
  }
}
