import { inject } from '@angular/core';
import { CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Còn thiết lập bắt buộc chưa xong (đổi MK mặc định / thêm avatar)?
 * → ép ở lại /account, chặn mọi route khác. /account không tự chặn (tránh vòng lặp).
 * Trả null nếu được phép đi tiếp, hoặc UrlTree /account để chuyển hướng.
 */
function setupRedirect(auth: AuthService, router: Router, state: RouterStateSnapshot) {
  const onAccount = state.url.split('?')[0] === '/account';
  if (!onAccount && auth.needsSetup()) {
    return router.parseUrl('/account');
  }
  return null;
}

/** Chặn route cần đăng nhập; phiên đã được khôi phục ở app initializer trước khi guard chạy. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.currentUser()) {
    return router.parseUrl('/login');
  }
  return setupRedirect(auth, router, state) ?? true;
};

/** Trang đăng nhập: nếu đã đăng nhập thì chuyển thẳng vào trang chủ (MXH). */
export const loginGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.currentUser() ? router.parseUrl('/home') : true;
};

/**
 * Chặn route theo CHỨC NĂNG (phân quyền ma trận). Chưa đăng nhập → /login;
 * còn thiết lập bắt buộc → /account; thiếu quyền chức năng → về /home (không lộ màn hình không có quyền).
 */
export const featureGuard = (feature: string): CanActivateFn => (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.currentUser()) {
    return router.parseUrl('/login');
  }
  const setup = setupRedirect(auth, router, state);
  if (setup) {
    return setup;
  }
  return auth.hasFeature(feature) ? true : router.parseUrl('/home');
};

/**
 * Guard độc lập ép thiết lập bắt buộc — tuỳ chọn gắn vào route nếu route nào đó KHÔNG dùng authGuard/featureGuard.
 * (authGuard và featureGuard đã tự kiểm; guard này dùng khi cần áp riêng.)
 */
export const setupGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.currentUser()) {
    return router.parseUrl('/login');
  }
  return setupRedirect(auth, router, state) ?? true;
};
