import { Component, computed, input, model, signal } from '@angular/core';
import { OrgUnit } from '../../core/org.service';

interface TreeRow {
  id: string;
  name: string;
  level: number;
  parentId: string | null;
}

/**
 * Cây cơ cấu tổ chức (sidebar): hiển thị đơn vị phân cấp cha-con, thụt lề theo cấp, gập/mở,
 * chọn node. Dùng cho màn Cơ cấu tổ chức và Vị trí/Chức danh.
 * <org-tree [units]="units()" [(selectedId)]="id" />
 */
@Component({
  selector: 'org-tree',
  template: `
    <div class="orgtree__toolbar">
      <span class="orgtree__count">{{ nodes().length }} đơn vị</span>
      <button type="button" class="orgtree__act" (click)="expandAll()">Mở hết</button>
      <button type="button" class="orgtree__act" (click)="collapseAll()">Gập hết</button>
    </div>
    <ul class="orgtree__list">
      @for (n of visible(); track n.id) {
        <li class="orgtree__row" [class.is-active]="selectedId() === n.id"
            [style.padding-left.px]="6 + n.level * 16" (click)="select(n.id)">
          @if (hasChildren(n.id)) {
            <button type="button" class="orgtree__toggle"
                    (click)="$event.stopPropagation(); toggle(n.id)"
                    [attr.aria-label]="isCollapsed(n.id) ? 'Mở' : 'Gập'">{{ isCollapsed(n.id) ? '▸' : '▾' }}</button>
          } @else { <span class="orgtree__leaf">·</span> }
          <span class="orgtree__ic">🏛️</span>
          <span class="orgtree__name">{{ n.name }}</span>
        </li>
      }
      @if (nodes().length === 0) { <li class="orgtree__empty">Chưa có đơn vị nào.</li> }
    </ul>
  `,
  styleUrl: './org-tree.scss'
})
export class OrgTree {
  readonly units = input<OrgUnit[]>([]);
  readonly selectedId = model<string | null>(null);
  readonly collapsed = signal<Set<string>>(new Set());

  /** Danh sách phẳng theo DFS (giữ thứ tự cây) + cấp thụt lề. */
  readonly nodes = computed<TreeRow[]>(() => {
    const byParent = new Map<string | null, OrgUnit[]>();
    for (const u of this.units()) {
      const k = u.parentId ?? null;
      (byParent.get(k) ?? byParent.set(k, []).get(k)!).push(u);
    }
    const out: TreeRow[] = [];
    const walk = (parentId: string | null, level: number) => {
      for (const u of byParent.get(parentId) ?? []) {
        out.push({ id: u.id, name: u.name, level, parentId: u.parentId ?? null });
        walk(u.id, level + 1);
      }
    };
    walk(null, 0);
    return out;
  });

  /** Ẩn node nếu có tổ tiên đang gập. */
  readonly visible = computed<TreeRow[]>(() => {
    const col = this.collapsed();
    const byId = new Map(this.nodes().map((n) => [n.id, n]));
    return this.nodes().filter((n) => {
      let p = n.parentId;
      while (p) {
        if (col.has(p)) return false;
        p = byId.get(p)?.parentId ?? null;
      }
      return true;
    });
  });

  hasChildren(id: string): boolean {
    return this.nodes().some((n) => n.parentId === id);
  }
  isCollapsed(id: string): boolean {
    return this.collapsed().has(id);
  }
  toggle(id: string): void {
    const s = new Set(this.collapsed());
    s.has(id) ? s.delete(id) : s.add(id);
    this.collapsed.set(s);
  }
  select(id: string): void {
    this.selectedId.set(id);
  }
  expandAll(): void {
    this.collapsed.set(new Set());
  }
  collapseAll(): void {
    this.collapsed.set(new Set(this.nodes().filter((n) => this.hasChildren(n.id)).map((n) => n.id)));
  }
}
