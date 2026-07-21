import { Injectable, inject } from '@angular/core';
import { AuthService } from './auth.service';

/** Lựa chọn đã nhớ của một user: dự án + task CHA gần nhất theo từng loại. */
export interface QuickCreatePrefs {
  projectId: string;
  /** Cha gần nhất theo loại (TASK/BUG/…) — mỗi loại có phân cấp cha khác nhau nên nhớ riêng. */
  parentByType: Record<string, string>;
}

const PREFIX = 'bpm.quickCreate.v1.';

/**
 * NHỚ LỰA CHỌN form Tạo nhanh / Log bug — giảm thao tác chọn lại mỗi lần.
 * Lưu ở localStorage THEO user đăng nhập (key kèm userId) nên máy dùng chung không lẫn dữ liệu.
 * Chỉ nhớ dự án + task cha; các trường nội dung (tiêu đề, mô tả…) luôn để trống.
 */
@Injectable({ providedIn: 'root' })
export class QuickCreatePrefsService {
  private auth = inject(AuthService);

  private key(): string | null {
    const uid = this.auth.currentUser()?.userId;
    return uid ? PREFIX + uid : null;
  }

  /** Đọc lựa chọn đã nhớ (null nếu chưa có / dữ liệu hỏng / chưa đăng nhập). */
  load(): QuickCreatePrefs | null {
    const k = this.key();
    if (!k) return null;
    try {
      const raw = localStorage.getItem(k);
      if (!raw) return null;
      const v = JSON.parse(raw) as Partial<QuickCreatePrefs>;
      if (!v || typeof v.projectId !== 'string') return null;
      return { projectId: v.projectId, parentByType: (v.parentByType as Record<string, string>) ?? {} };
    } catch {
      return null; // localStorage bị chặn hoặc JSON hỏng → coi như chưa nhớ gì
    }
  }

  /** Ghi nhớ sau khi tạo THÀNH CÔNG. parentId rỗng → xoá cha đã nhớ của loại đó. */
  save(type: string, projectId: string, parentId: string | null): void {
    const k = this.key();
    if (!k || !projectId) return;
    const cur = this.load();
    // Đổi dự án → cha cũ thuộc dự án khác, bỏ hết.
    const parentByType = cur && cur.projectId === projectId ? { ...cur.parentByType } : {};
    if (parentId) parentByType[type] = parentId;
    else delete parentByType[type];
    try {
      localStorage.setItem(k, JSON.stringify({ projectId, parentByType }));
    } catch {
      /* hết quota / chế độ riêng tư — bỏ qua, không chặn luồng tạo task */
    }
  }
}
