import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { App } from './app';
import { AuthService } from './core/auth.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient()]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('hiện thương hiệu ở sidebar khi đã đăng nhập', () => {
    const auth = TestBed.inject(AuthService);
    auth.currentUser.set({ username: 'admin', authorities: [{ authority: 'ROLE_ADMIN' }] });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.sidebar__brand')?.textContent).toContain('Plan X');
  });

  it('không render shell khi chưa đăng nhập', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.app-shell')).toBeNull();
  });

  it('ẩn menu quản trị khi chưa đăng nhập', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.main-nav a').length).toBe(0);
  });

  it('hiện menu quản trị khi có ROLE_ADMIN (AC-4)', () => {
    const auth = TestBed.inject(AuthService);
    auth.currentUser.set({ username: 'admin', authorities: [{ authority: 'ROLE_ADMIN' }] });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    // Cá nhân 6 · Dự án 1 · Báo cáo 2 · Công cụ 1 · Quản trị 9 (+Phân quyền) = 19
    expect(compiled.querySelectorAll('.main-nav a').length).toBe(19);
  });
});
