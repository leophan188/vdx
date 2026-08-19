import { Routes } from '@angular/router';
import { Login } from './login/login';
import { Accounts } from './accounts/accounts';
import { Org } from './org/org';
import { Positions } from './positions/positions';
import { Roles } from './roles/roles';
import { Audit } from './audit/audit';
import { Styleguide } from './styleguide/styleguide';
import { Dashboard } from './dashboard/dashboard';
import { Home } from './home/home';
import { Processes } from './processes/processes';
import { Forms } from './forms/forms';
import { Inbox } from './tasks/inbox';
import { Tracking } from './tracking/tracking';
import { NewRequest } from './requests/new-request';
import { MyRequests } from './requests/my-requests';
import { Ot } from './ot/ot';
import { Leave } from './leave/leave';
import { ExcelReports } from './excel-reports/excel-reports';
import { Reports } from './reports/reports';
import { Documents } from './documents/documents';
import { DocEditor } from './documents/doc-editor';
import { Catalog } from './catalog/catalog';
import { Account } from './account/account';
import { authGuard, loginGuard, featureGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'home' },
  { path: 'login', component: Login, canActivate: [loginGuard] },
  { path: 'home', component: Home, canActivate: [authGuard] },
  { path: 'account', component: Account, canActivate: [authGuard] },
  { path: 'dashboard', component: Dashboard, canActivate: [featureGuard('REPORTS')] },
  { path: 'inbox', component: Inbox, canActivate: [featureGuard('INBOX')] },
  { path: 'new-request', component: NewRequest, canActivate: [featureGuard('REQUESTS')] },
  { path: 'my-requests', component: MyRequests, canActivate: [featureGuard('REQUESTS')] },
  { path: 'documents', component: Documents, canActivate: [featureGuard('DOCS')] },
  { path: 'documents/:id', component: DocEditor, canActivate: [featureGuard('DOCS')] },
  { path: 'ot', component: Ot, canActivate: [featureGuard('OT')] },
  { path: 'leave', component: Leave, canActivate: [featureGuard('LEAVE')] },
  { path: 'employees', loadComponent: () => import('./employees/employees').then((m) => m.Employees), canActivate: [featureGuard('HR')] },
  // Danh mục hệ thống — gom các danh mục khai báo vào 1 màn (menu trái + router con).
  {
    path: 'catalog', component: Catalog, canActivate: [featureGuard('CATALOG')],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'org' },
      { path: 'org', component: Org },
      { path: 'positions', component: Positions },
      { path: 'roles', component: Roles }
    ]
  },
  { path: 'excel-reports', component: ExcelReports, canActivate: [featureGuard('IMPORT')] },
  { path: 'tools', pathMatch: 'full', redirectTo: 'excel-reports' }, // lối vào theo tên gọi "Công cụ"
  { path: 'system-config', loadComponent: () => import('./system-config/system-config').then((m) => m.SystemConfig), canActivate: [featureGuard('SYSTEM')] },
  { path: 'permissions', loadComponent: () => import('./permissions/permission-matrix').then((m) => m.PermissionMatrix), canActivate: [featureGuard('PERMISSION')] },
  { path: 'my-tasks', loadComponent: () => import('./projects/my-work/my-work').then((m) => m.MyProjectWork), canActivate: [featureGuard('MYTASKS')] },
  { path: 'projects', loadComponent: () => import('./projects/projects').then((m) => m.Projects), canActivate: [featureGuard('PROJECT')] },
  { path: 'projects/:id', loadComponent: () => import('./projects/project-workspace').then((m) => m.ProjectWorkspace), canActivate: [featureGuard('PROJECT')] },
  { path: 'tracking', component: Tracking, canActivate: [featureGuard('TRACKING')] },
  { path: 'reports', component: Reports, canActivate: [featureGuard('REPORTS')] },
  { path: 'accounts', component: Accounts, canActivate: [featureGuard('ACCOUNTS')] },
  { path: 'org', component: Org, canActivate: [featureGuard('CATALOG')] },
  { path: 'positions', component: Positions, canActivate: [featureGuard('CATALOG')] },
  { path: 'roles', component: Roles, canActivate: [featureGuard('CATALOG')] },
  { path: 'processes', component: Processes, canActivate: [featureGuard('PROCESS')] },
  {
    path: 'processes/:id',
    canActivate: [featureGuard('PROCESS')],
    loadComponent: () => import('./processes/designer/designer').then((m) => m.Designer)
  },
  { path: 'forms', component: Forms, canActivate: [featureGuard('PROCESS')] },
  {
    path: 'forms/:id',
    canActivate: [featureGuard('PROCESS')],
    loadComponent: () => import('./forms/builder/builder').then((m) => m.Builder)
  },
  { path: 'audit', component: Audit, canActivate: [featureGuard('AUDIT')] },
  { path: 'styleguide', component: Styleguide, canActivate: [authGuard] }
];
