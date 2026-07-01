import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Login } from './login';
import { AuthService } from '../core/auth.service';

describe('Login', () => {
  function setup(authSpy: Partial<AuthService>) {
    const routerStub = { navigate: () => Promise.resolve(true) } as unknown as Router;
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        { provide: Router, useValue: routerStub },
        { provide: AuthService, useValue: authSpy }
      ]
    });
    const fixture = TestBed.createComponent(Login);
    return { fixture, cmp: fixture.componentInstance };
  }

  it('hiển thị lỗi khi đăng nhập thất bại', () => {
    const { cmp } = setup({ login: () => throwError(() => new Error('401')) } as Partial<AuthService>);
    cmp.username = 'alice';
    cmp.password = 'wrong';
    cmp.submit();
    expect(cmp.error()).toContain('không đúng');
    expect(cmp.loading()).toBeFalsy();
  });

  it('không lỗi khi đăng nhập thành công', () => {
    const result = { username: 'alice', authorities: [] };
    const { cmp } = setup({ login: () => of(result) } as unknown as Partial<AuthService>);
    cmp.username = 'alice';
    cmp.password = 'Secret123';
    cmp.submit();
    expect(cmp.error()).toBeNull();
  });
});
