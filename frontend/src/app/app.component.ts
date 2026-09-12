import { Component, computed } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';
import { ROLE_LABELS } from './core/labels';

// The shell: a nav bar for a logged-in user, then the current page.
// The menu links come with the screens in roadmap step 8.
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    @if (user(); as user) {
      <nav class="navbar">
        <a class="navbar-brand" routerLink="/">Kricco</a>
        <div class="navbar-links"></div>
        <div class="navbar-user">
          <span>{{ user.displayName }} ({{ roleLabel() }})</span>
          <button class="btn-logout" (click)="logout()">Odjava</button>
        </div>
      </nav>
    }
    <main [class.with-nav]="user()">
      <router-outlet />
    </main>
  `,
  styleUrl: './app.component.scss',
})
export class AppComponent {
  user = computed(() => this.auth.currentUser());
  roleLabel = computed(() => {
    const role = this.auth.currentUser()?.role;
    return role ? ROLE_LABELS[role] : '';
  });

  constructor(private auth: AuthService) {}

  logout() {
    this.auth.logout();
  }
}
