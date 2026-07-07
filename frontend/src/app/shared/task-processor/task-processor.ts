import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Modal } from '../modal/modal';
import { ToastService } from '../toast/toast.service';
import { WorkflowService, TaskDetail } from '../../core/workflow.service';
import { FormService } from '../../core/form.service';

type Perm = 'EDIT' | 'READONLY' | 'HIDDEN';
interface RField {
  key: string; label: string; type: string; required?: boolean;
  placeholder?: string; options?: string;
}

/**
 * Modal xử lý một việc (Story 3.4) dùng chung: render biểu mẫu gắn bước theo quyền trường + nút hành động.
 * Mở bằng openTask(taskId); phát (completed) khi hoàn thành để màn cha tải lại.
 */
@Component({
  selector: 'app-task-processor',
  imports: [FormsModule, Modal],
  templateUrl: './task-processor.html'
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
  /** Mở/gập mục "Dữ liệu các bước trước". */
  readonly priorOpen = signal(false);
  togglePrior(): void { this.priorOpen.update((v) => !v); }

  /** Mở việc theo id: tải chi tiết + schema biểu mẫu. */
  openTask(taskId: string): void {
    this.busy.set(false);
    this.fields.set([]);
    this.values.set({});
    this.detail.set(null);
    this.priorOpen.set(false);
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
