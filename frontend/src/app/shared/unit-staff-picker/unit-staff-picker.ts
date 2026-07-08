import { Component, computed, inject, input, model, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Modal } from '../modal/modal';
import { OrgService, OrgUnit } from '../../core/org.service';
import { PositionService, Position } from '../../core/position.service';
import { AuthService, UserAccount } from '../../core/auth.service';

interface UnitNode { id: string; name: string; parentId: string | null; level: number; hasChildren: boolean; }

/**
 * Chọn NHÂN SỰ theo CÂY ĐƠN VỊ (popup 2 cột): chọn đơn vị ở cây → nạp nhân sự của đơn vị
 * (gồm đơn vị con) → chọn 1 người. Trả về userId qua [(value)].
 * Dùng: <unit-staff-picker [(value)]="userId" />
 */
@Component({
  selector: 'unit-staff-picker',
  imports: [FormsModule, Modal],
  templateUrl: './unit-staff-picker.html',
  styles: [`
    .usp-trigger { display: inline-flex; align-items: center; gap: 8px; width: 100%;
      min-height: var(--control-h-sm, 34px); border: 1px solid var(--color-border); border-radius: var(--radius-md, 8px);
      background: var(--color-surface); color: var(--color-text); padding: 4px 10px; cursor: pointer; font: inherit; box-sizing: border-box; }
    .usp-trigger:hover { border-color: var(--color-primary); }
    .usp-trigger__val { flex: 1; text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .usp-trigger__val--empty { color: var(--color-text-muted); }
    .usp-clear { border: 0; background: none; cursor: pointer; color: var(--color-text-muted); font-size: 1rem; padding: 0 2px; }
    .usp-clear:hover { color: var(--status-cancel, #dc2626); }

    .usp-body { display: grid; grid-template-columns: minmax(220px, 1fr) minmax(240px, 1.2fr); gap: 12px; min-height: 340px; }
    @media (max-width: 640px) { .usp-body { grid-template-columns: 1fr; } }
    .usp-col { border: 1px solid var(--color-border); border-radius: 8px; overflow: hidden; display: flex; flex-direction: column; }
    .usp-col__head { padding: 8px 10px; font-weight: 600; font-size: .85em; background: var(--color-surface-2, rgba(127,127,127,.06));
      border-bottom: 1px solid var(--color-border); }
    .usp-col__scroll { overflow: auto; max-height: 420px; }

    .usp-unit { width: 100%; display: flex; align-items: center; gap: 6px; padding: 6px 8px; background: transparent;
      border: 0; cursor: pointer; text-align: left; color: inherit; font: inherit; }
    .usp-unit:hover { background: var(--color-surface-2, rgba(127,127,127,.08)); }
    .usp-unit.is-active { background: var(--color-primary-soft, rgba(30,80,160,.14)); font-weight: 600; }
    .usp-unit__caret { flex: 0 0 18px; text-align: center; opacity: .6; }
    .usp-unit__name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

    .usp-search { width: 100%; box-sizing: border-box; border: 0; border-bottom: 1px solid var(--color-border);
      background: var(--color-surface); color: var(--color-text); padding: 7px 10px; font: inherit; }
    .usp-person { width: 100%; display: flex; align-items: center; gap: 8px; padding: 7px 10px; background: transparent;
      border: 0; border-bottom: 1px solid var(--color-border); cursor: pointer; text-align: left; color: inherit; font: inherit; }
    .usp-person:hover { background: var(--color-surface-2, rgba(127,127,127,.08)); }
    .usp-person.is-active { background: var(--color-primary-soft, rgba(30,80,160,.14)); }
    .usp-person__ava { flex: 0 0 26px; height: 26px; border-radius: 50%; background: var(--color-primary, #1e50a0); color: #fff;
      display: inline-flex; align-items: center; justify-content: center; font-size: .78em; font-weight: 700; }
    .usp-person__name { flex: 1; min-width: 0; }
    .usp-person__name small { display: block; opacity: .65; font-size: .82em; }
    .usp-empty { padding: 16px; text-align: center; color: var(--color-text-muted); font-size: .9em; }
  `]
})
export class UnitStaffPicker implements OnInit {
  private orgSvc = inject(OrgService);
  private positionSvc = inject(PositionService);
  private authSvc = inject(AuthService);

  /** userId đã chọn (2 chiều). */
  readonly value = model<string>('');
  readonly placeholder = input('— Chọn nhân sự —');
  readonly disabled = input(false);

  private readonly units = signal<OrgUnit[]>([]);
  private readonly positions = signal<Position[]>([]);
  private readonly users = signal<UserAccount[]>([]);

  readonly open = signal(false);
  readonly selectedUnit = signal<string>('');
  readonly search = signal('');
  readonly expanded = signal<Set<string>>(new Set());

  ngOnInit(): void {
    this.orgSvc.all().subscribe({ next: (u) => this.units.set(u), error: () => {} });
    this.positionSvc.all().subscribe({ next: (p) => this.positions.set(p), error: () => {} });
    this.authSvc.listUsers().subscribe({ next: (u) => this.users.set(u), error: () => {} });
  }

  private readonly userById = computed(() => new Map(this.users().map((u) => [u.id, u])));

  /** Nhãn người đã chọn (hiển thị ở ô trigger). */
  readonly selectedLabel = computed<string>(() => {
    const u = this.userById().get(this.value());
    return u ? `${u.fullName} (${u.username})` : '';
  });

  // ----- Cây đơn vị (phẳng + cấp, lọc theo expanded) -----
  private readonly allUnitNodes = computed<UnitNode[]>(() => {
    const byParent = new Map<string | null, OrgUnit[]>();
    for (const u of this.units()) {
      const k = u.parentId ?? null;
      (byParent.get(k) ?? byParent.set(k, []).get(k)!).push(u);
    }
    const out: UnitNode[] = [];
    const walk = (u: OrgUnit, level: number) => {
      const kids = byParent.get(u.id) ?? [];
      out.push({ id: u.id, name: u.name, parentId: u.parentId ?? null, level, hasChildren: kids.length > 0 });
      for (const c of kids) walk(c, level + 1);
    };
    for (const root of byParent.get(null) ?? []) walk(root, 0);
    return out;
  });
  readonly unitNodes = computed<UnitNode[]>(() => {
    const exp = this.expanded();
    const byId = new Map(this.allUnitNodes().map((n) => [n.id, n]));
    return this.allUnitNodes().filter((n) => {
      let p = n.parentId;
      while (p) { if (!exp.has(p)) return false; p = byId.get(p)?.parentId ?? null; }
      return true;
    });
  });

  /** Tập id đơn vị = đơn vị chọn + mọi đơn vị con (đệ quy). */
  private descendantUnitIds(unitId: string): Set<string> {
    const byParent = new Map<string | null, OrgUnit[]>();
    for (const u of this.units()) {
      const k = u.parentId ?? null;
      (byParent.get(k) ?? byParent.set(k, []).get(k)!).push(u);
    }
    const set = new Set<string>();
    const walk = (id: string) => { set.add(id); for (const c of byParent.get(id) ?? []) walk(c.id); };
    walk(unitId);
    return set;
  }

  /** Nhân sự của đơn vị đang chọn (giữ vị trí trong đơn vị + đơn vị con), lọc theo ô tìm. */
  readonly staff = computed<UserAccount[]>(() => {
    const unit = this.selectedUnit();
    if (!unit) return [];
    const scope = this.descendantUnitIds(unit);
    const ids = new Set<string>();
    for (const p of this.positions()) {
      if (p.orgUnitId && scope.has(p.orgUnitId) && p.currentHolderUserId) ids.add(p.currentHolderUserId);
    }
    const byId = this.userById();
    let list = [...ids].map((id) => byId.get(id)).filter((u): u is UserAccount => !!u);
    const q = this.search().trim().toLowerCase();
    if (q) list = list.filter((u) => (`${u.fullName} ${u.username}`).toLowerCase().includes(q));
    return list.sort((a, b) => a.fullName.localeCompare(b.fullName, 'vi'));
  });

  // ----- Tương tác -----
  openPicker(): void {
    if (this.disabled()) return;
    this.search.set('');
    // Mở sẵn cấp gốc + đơn vị đang chọn (nếu có).
    this.expanded.set(new Set(this.allUnitNodes().filter((n) => n.level === 0 && n.hasChildren).map((n) => n.id)));
    this.open.set(true);
  }
  toggleUnit(id: string): void {
    const s = new Set(this.expanded());
    s.has(id) ? s.delete(id) : s.add(id);
    this.expanded.set(s);
  }
  pickUnit(id: string): void { this.selectedUnit.set(id); }
  pickUser(id: string): void { this.value.set(id); this.open.set(false); }
  clear(ev: Event): void { ev.stopPropagation(); this.value.set(''); }
  initials(name: string): string {
    const parts = (name || '').trim().split(/\s+/);
    return ((parts[0]?.[0] ?? '') + (parts[parts.length - 1]?.[0] ?? '')).toUpperCase() || '?';
  }
  isCurrent(id: string): boolean { return this.value() === id; }
}
