import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { PageHeader } from '../shared/page-header/page-header';

/**
 * Màn "Danh mục hệ thống" — gom các danh mục khai báo (cơ cấu, vị trí, vai trò, tài khoản, nhân sự)
 * vào MỘT màn hình với menu dạng cây bên trái + nội dung danh mục bên phải (router con).
 */
@Component({
  selector: 'app-catalog',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, PageHeader],
  template: `
    <section class="page">
      <app-page-header title="Danh mục hệ thống"
        subtitle="Khai báo tập trung: cơ cấu tổ chức, vị trí/chức danh, vai trò, tài khoản, nhân sự."
        [breadcrumb]="[{ label: 'Quản trị' }, { label: 'Danh mục' }]" />
      <div class="catalog">
        <nav class="catalog__menu card" aria-label="Danh mục">
          <a routerLink="org" routerLinkActive="is-active"><span class="catalog__ic">🏛️</span><span>Cơ cấu tổ chức</span></a>
          <a routerLink="positions" routerLinkActive="is-active"><span class="catalog__ic">🪪</span><span>Vị trí / Chức danh</span></a>
          <a routerLink="roles" routerLinkActive="is-active"><span class="catalog__ic">🛡️</span><span>Vai trò</span></a>
        </nav>
        <div class="catalog__main"><router-outlet /></div>
      </div>
    </section>
  `,
  styles: [`
    .catalog { display: grid; grid-template-columns: 230px minmax(0, 1fr); gap: var(--space-4); align-items: start; }
    .catalog__menu { display: flex; flex-direction: column; gap: 2px; padding: var(--space-2); position: sticky; top: 0; }
    .catalog__menu a { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-2) var(--space-3);
      border-radius: var(--radius-md); color: var(--color-text-muted); text-decoration: none; font-weight: var(--weight-medium); white-space: nowrap; }
    .catalog__menu a:hover { background: var(--color-surface-alt); color: var(--color-text); }
    .catalog__menu a.is-active { background: var(--color-primary-soft); color: var(--color-primary); font-weight: var(--weight-semibold); }
    .catalog__ic { width: 18px; text-align: center; }
    /* Bỏ padding/section trùng của trang con khi nhúng trong danh mục */
    .catalog__main { min-width: 0; }
    .catalog__main ::ng-deep .page { padding: 0; }
    @media (max-width: 900px) { .catalog { grid-template-columns: 1fr; } }
  `]
})
export class Catalog {}
