import { Component, computed, inject, input, model, signal, OnInit } from '@angular/core';
import { OrgService, OrgUnit } from '../../core/org.service';
import { PositionService, Position } from '../../core/position.service';
import { AuthService, UserAccount } from '../../core/auth.service';
import { Modal } from '../modal/modal';
import { NgTemplateOutlet } from '@angular/common';

type PickMode = 'unit' | 'position' | 'user';
interface PickNode {
  key: string;
  parentKey: string | null;
  id: string;
  label: string;
  type: PickMode;
  level: number;
  selectable: boolean;
}

/**
 * Chọn nhân sự / chức danh / đơn vị theo CÂY tổ chức (Story 2.7) — dùng chung cho form lẫn phân công.
 * <org-tree-picker mode="user|position|unit" [(value)]="id">
 */
@Component({
  selector: 'org-tree-picker',
  imports: [Modal, NgTemplateOutlet],
  templateUrl: './org-tree-picker.html',
  styles: [`
    .otp-trigger{display:inline-flex;align-items:center;gap:8px;width:100%;min-height:var(--control-h-sm,34px);
      border:1px solid var(--color-border);border-radius:var(--radius-md,8px);background:var(--color-surface);
      color:var(--color-text);padding:4px 10px;cursor:pointer;font:inherit;box-sizing:border-box;}
    .otp-trigger:hover{border-color:var(--color-primary);}
    .otp-trigger:disabled{opacity:.6;cursor:not-allowed;}
    .otp-trigger__val{flex:1;text-align:left;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
    .otp-trigger__val--empty{color:var(--color-text-muted);}
  `]
})
export class OrgTreePicker implements OnInit {
  private orgSvc = inject(OrgService);
  private positionSvc = inject(PositionService);
  private authSvc = inject(AuthService);

  readonly mode = input<PickMode>('user');
  /** Hiển thị dạng nút bấm mở popup (dùng trong ô hẹp như cột bảng) thay vì cây inline. */
  readonly popup = input(false);
  readonly disabled = input(false);
  readonly open = signal(false);
  readonly value = model<string>('');

  private readonly units = signal<OrgUnit[]>([]);
  private readonly positions = signal<Position[]>([]);
  private readonly users = signal<UserAccount[]>([]);
  readonly expanded = signal<Set<string>>(new Set());

  readonly nodes = computed<PickNode[]>(() => this.build());
  readonly visible = computed<PickNode[]>(() => {
    const exp = this.expanded();
    const byKey = new Map(this.nodes().map((n) => [n.key, n]));
    return this.nodes().filter((n) => {
      let p = n.parentKey;
      while (p) {
        if (!exp.has(p)) return false;
        p = byKey.get(p)?.parentKey ?? null;
      }
      return true;
    });
  });
  readonly selectedLabel = computed(() => {
    const v = this.value();
    return this.nodes().find((n) => n.selectable && n.id === v)?.label ?? '';
  });

  ngOnInit(): void {
    this.orgSvc.all().subscribe({ next: (u) => this.units.set(u), error: () => {} });
    this.positionSvc.all().subscribe({ next: (p) => this.positions.set(p), error: () => {} });
    this.authSvc.listUsers().subscribe({ next: (u) => this.users.set(u), error: () => {} });
  }

  private build(): PickNode[] {
    const mode = this.mode();
    const byParent = new Map<string | null, OrgUnit[]>();
    for (const u of this.units()) {
      const k = u.parentId ?? null;
      (byParent.get(k) ?? byParent.set(k, []).get(k)!).push(u);
    }
    const posByUnit = new Map<string, Position[]>();
    for (const p of this.positions()) (posByUnit.get(p.orgUnitId) ?? posByUnit.set(p.orgUnitId, []).get(p.orgUnitId)!).push(p);
    const userById = new Map(this.users().map((u) => [u.id, u]));
    const unitNameById = new Map(this.units().map((u) => [u.id, u.name]));
    const out: PickNode[] = [];
    const walk = (u: OrgUnit, level: number, parentKey: string | null) => {
      const uk = 'u:' + u.id;
      out.push({ key: uk, parentKey, id: u.id, label: u.name, type: 'unit', level, selectable: mode === 'unit' });
      for (const c of byParent.get(u.id) ?? []) walk(c, level + 1, uk);
      for (const p of posByUnit.get(u.id) ?? []) {
        const pk = 'p:' + p.id;
        out.push({ key: pk, parentKey: uk, id: p.id, label: p.title, type: 'position', level: level + 1, selectable: mode === 'position' });
        if (mode === 'user' && p.currentHolderUserId) {
          const usr = userById.get(p.currentHolderUserId);
          if (usr) {
            // Nhãn đủ thông tin: Tên (mã NV) — chức vụ · bộ phận.
            const tail = [p.title, unitNameById.get(p.orgUnitId)].filter(Boolean).join(' · ');
            const label = usr.fullName + ' (' + usr.username + ')' + (tail ? ' — ' + tail : '');
            out.push({ key: 'usr:' + p.id, parentKey: pk, id: usr.id, label, type: 'user', level: level + 2, selectable: true });
          }
        }
      }
    };
    for (const root of byParent.get(null) ?? []) walk(root, 0, null);
    return out;
  }

  hasChildren(key: string): boolean {
    return this.nodes().some((n) => n.parentKey === key);
  }
  toggle(key: string): void {
    const s = new Set(this.expanded());
    s.has(key) ? s.delete(key) : s.add(key);
    this.expanded.set(s);
  }
  select(n: PickNode): void {
    if (n.selectable) {
      this.value.set(n.id);
      if (this.popup()) this.open.set(false); // chọn xong tự đóng popup
    }
  }
  openPopup(): void {
    if (this.disabled()) return;
    this.expandAll(); // mở sẵn để dễ chọn trong popup
    this.open.set(true);
  }
  placeholderText(): string {
    return 'Chọn ' + (this.mode() === 'unit' ? 'đơn vị' : this.mode() === 'position' ? 'chức danh' : 'nhân sự') + '…';
  }
  icon(t: PickMode): string {
    return t === 'unit' ? '🏛️' : t === 'position' ? '🪪' : '👤';
  }
  expandAll(): void {
    this.expanded.set(new Set(this.nodes().filter((n) => this.hasChildren(n.key)).map((n) => n.key)));
  }
}
