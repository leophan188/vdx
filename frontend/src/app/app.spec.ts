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

  /**
   * Điều hướng nay là: TOOLBAR ngang chọn MODULE → sidebar hiện chức năng con của module đó.
   * Nên không còn cảnh mọi link nằm cùng lúc trong .main-nav (thiết kế menu phẳng cũ);
   * điều đáng khẳng định là admin thấy ĐỦ module, trong đó có "Quản trị hệ thống".
   */
  it('hiện đủ module khi có ROLE_ADMIN (AC-4)', () => {
    const auth = TestBed.inject(AuthService);
    auth.currentUser.set({ username: 'admin', authorities: [{ authority: 'ROLE_ADMIN' }] });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    const modules = Array.from(compiled.querySelectorAll('.topbar__module'))
      .map((b) => b.textContent?.trim() ?? '');
    expect(modules.length).toBe(5);
    expect(modules.some((m) => m.includes('Quản trị hệ thống'))).toBe(true);

    // Sidebar hiện chức năng con của module đang chọn (mặc định "Cá nhân"), không phải toàn bộ link.
    const links = compiled.querySelectorAll('.main-nav a');
    expect(links.length).toBeGreaterThan(0);
    expect(links.length).toBeLessThan(modules.length * 10);
  });
});
