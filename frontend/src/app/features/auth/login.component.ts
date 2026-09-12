import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="login-wrap">
      <form class="login-box" (ngSubmit)="login()">
        <div class="login-logo">Kricco</div>
        <h2>Prijava</h2>
        <div class="form-group">
          <label for="username">Korisničko ime</label>
          <input id="username" name="username" [(ngModel)]="username" type="text" autocomplete="username" />
        </div>
        <div class="form-group">
          <label for="password">Lozinka</label>
          <input id="password" name="password" [(ngModel)]="password" type="password" autocomplete="current-password" />
        </div>
        @if (error) {
          <div class="error">{{ error }}</div>
        }
        <button class="btn btn-primary login-button" type="submit" [disabled]="loading">
          {{ loading ? 'Prijava...' : 'Prijava' }}
        </button>
      </form>
    </div>
  `,
  styles: [`
    .login-wrap {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 16px;
      background: #f5f5f5;
    }
    .login-box {
      background: white;
      padding: 36px 32px;
      border-radius: 8px;
      box-shadow: 0 2px 12px rgba(0,0,0,0.15);
      width: 100%;
      max-width: 360px;
    }
    .login-logo {
      font-size: 1.6rem;
      font-weight: 700;
      color: #2e7d32;
      text-align: center;
      margin-bottom: 8px;
    }
    h2 { text-align: center; color: #555; margin-bottom: 24px; }
    .error { color: #c62828; font-size: 0.85rem; margin-bottom: 10px; }
    .login-button { width: 100%; }
  `],
})
export class LoginComponent {
  username = '';
  password = '';
  error = '';
  loading = false;

  constructor(private auth: AuthService, private router: Router) {}

  login() {
    if (!this.username || !this.password) {
      return;
    }
    this.loading = true;
    this.error = '';
    this.auth.login({ username: this.username, password: this.password }).subscribe({
      next: () => this.router.navigate(['/']),
      error: (response: HttpErrorResponse) => {
        this.error = loginErrorText(response);
        this.loading = false;
      },
    });
  }
}

// 401 = wrong username or password, or a deactivated account (the backend doesn't say which).
// Anything else means the backend isn't reachable or failed.
function loginErrorText(response: HttpErrorResponse): string {
  if (response.status === 401) {
    return 'Pogrešno korisničko ime ili lozinka.';
  }
  return 'Prijava trenutno nije moguća. Pokušajte kasnije.';
}
