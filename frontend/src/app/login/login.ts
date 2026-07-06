import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ThemeService } from '../shared/theme.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class Login {
  private auth = inject(AuthService);
  private router = inject(Router);
  protected readonly themeSvc = inject(ThemeService);

  // Ô đăng nhập để TRỐNG (không prefill credential — an toàn cho production).
  username = '';
  password = '';
  rememberMe = true;
  readonly showPassword = signal(false);
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);

  togglePassword(): void {
    this.showPassword.update((v) => !v);
  }

  submit(): void {
    if (this.loading()) return;
    this.error.set(null);
    this.loading.set(true);
    this.auth.login(this.username, this.password, this.rememberMe).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/home']);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Tên đăng nhập hoặc mật khẩu không đúng');
      }
    });
  }
}
