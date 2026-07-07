import { Component, computed, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Modal } from '../modal/modal';
import { ToastService } from '../toast/toast.service';
import { WorkflowService, TaskDetail, StepView } from '../../core/workflow.service';
import { FormService } from '../../core/form.service';

type Perm = 'EDIT' | 'READONLY' | 'HIDDEN';
interface RField {
  key: string; label: string; type: string; required?: boolean;
  placeholder?: string; options?: string;
}

/**
 * Modal xử lý một việc (Story 3.4) dùng chung: render biểu mẫu gắn bước theo quyền trường + nút hành động.
 * Hiển thị NỘI DUNG YÊU CẦU (bước đầu) + DỮ LIỆU TỪ BƯỚC TRƯỚC (accordion từng bước, gập mặc định).
 * Mở bằng openTask(taskId); phát (completed) khi hoàn thành để màn cha tải lại.
 */
@Component({
  selector: 'app-task-processor',
  imports: [FormsModule, Modal],
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
  `]
})
export class TaskProcessor {
  private wf = inject(WorkflowService);
  private formSvc = inject(FormService);
  private toast = inject(ToastService);

  /** Phát khi việc đã hoàn thành (màn cha tải lại danh sách). */
  readonly completed = output<void>();

  readonly open = signal(false);
  readonly detail = signal<TaskDetail | null>(null);
  readonly fields = signal<RField[]>([]);
  readonly values = signal<Record<string, unknown>>({});
  readonly busy = signal(false);

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
    this.values.set({});
    this.detail.set(null);
    this.expandedPrior.set(new Set());
    this.open.set(true);
    this.wf.detail(taskId).subscribe({
      next: (d) => {
        this.detail.set(d);
        this.values.set({ ...d.formData });
        if (d.formId) {
          this.formSvc.get(d.formId).subscribe({
            next: (f) => this.fields.set(this.parseFields(f.schemaJson)),
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

  private parseFields(schemaJson: string | null): RField[] {
    if (!schemaJson) return [];
    try {
      const parsed = JSON.parse(schemaJson);
      const arr = Array.isArray(parsed) ? parsed : (parsed.fields ?? []);
      return arr.map((f: Record<string, unknown>) => ({
        key: String(f['key'] ?? ''), label: String(f['label'] ?? f['key'] ?? ''),
        type: String(f['type'] ?? 'text'), required: !!f['required'],
        placeholder: f['placeholder'] as string, options: f['options'] as string
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

  /** Hoàn thành việc với hành động đã chọn. */
  act(action: string): void {
    const d = this.detail();
    if (!d) return;
    const missing = this.fields().filter((f) =>
      f.required && this.visible(f.key) && !this.readonly_(f.key) &&
      (this.val(f.key) === undefined || this.val(f.key) === null || this.val(f.key) === ''));
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
