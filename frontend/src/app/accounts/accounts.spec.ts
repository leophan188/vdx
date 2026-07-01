import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Accounts } from './accounts';
import { AuthService, UserAccount } from '../core/auth.service';

const sample: UserAccount[] = [
  { id: '1', username: 'admin1', fullName: 'Quản trị', email: null, phone: null, status: 'ACTIVE', role: 'ADMIN', roleCode: null },
  { id: '2', username: 'bob', fullName: 'Bob', email: null, phone: null, status: 'LOCKED', role: 'USER', roleCode: 'NHANSU' }
];

describe('Accounts', () => {
  function setup(auth: Partial<AuthService>) {
    TestBed.configureTestingModule({
      imports: [Accounts],
      providers: [{ provide: AuthService, useValue: auth }, provideRouter([])]
    });
    const fixture = TestBed.createComponent(Accounts);
    return { fixture, cmp: fixture.componentInstance };
  }

  it('tải danh sách khi khởi tạo', () => {
    const { cmp, fixture } = setup({ listUsers: () => of(sample) } as Partial<AuthService>);
    fixture.detectChanges();
    expect(cmp.users().length).toBe(2);
    expect(cmp.users()[0].username).toBe('admin1');
  });

  it('hiển thị lỗi khi không đủ quyền', () => {
    const { cmp, fixture } = setup({ listUsers: () => throwError(() => new Error('403')) } as Partial<AuthService>);
    fixture.detectChanges();
    expect(cmp.error()).toContain('quản trị');
  });

  it('wizard tạo tài khoản gọi service với payload đúng', () => {
    let payload: { username: string } | null = null;
    const auth = {
      listUsers: () => of(sample),
      createUser: (p: { username: string }) => { payload = p; return of(sample[0]); }
    } as unknown as Partial<AuthService>;
    const { cmp } = setup(auth);
    cmp.openCreate();
    cmp.w.username = 'carol';
    cmp.w.fullName = 'Carol';
    cmp.w.password = 'Secret123';
    expect(cmp.canNext()).toBe(true);
    cmp.submitCreate();
    expect(payload).toBeTruthy();
    expect(payload!.username).toBe('carol');
    expect(cmp.createOpen()).toBe(false);
  });

  it('bộ lọc theo trạng thái lọc đúng', () => {
    const { cmp, fixture } = setup({ listUsers: () => of(sample) } as Partial<AuthService>);
    fixture.detectChanges();
    cmp.filterStatus = 'LOCKED';
    cmp.applyFilter();
    expect(cmp.filteredUsers().length).toBe(1);
    expect(cmp.filteredUsers()[0].username).toBe('bob');
  });
});
