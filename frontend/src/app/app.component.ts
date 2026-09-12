import { Component, computed } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './core/services/auth.service';
import { ROLE_LABELS } from './core/labels';

// The shell: a nav bar with the links of the user's role, then the current page.
// OFFICE uses the admin page too, but only for clients (and read-only lists), so its link says "Klijenti".
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, CommonModule],
  template: `
    <nav class="navbar" *ngIf="isLoggedIn()">
      <a class="navbar-brand" routerLink="/">Kricco</a>
      <div class="navbar-links">
        <a routerLink="/servicer" *ngIf="isServicer()">Početna</a>
        <a routerLink="/orders" *ngIf="isEmployee()">Nalozi</a>
        <a routerLink="/admin" *ngIf="isAdmin()">Admin</a>
        <a routerLink="/admin" *ngIf="isOffice()">Klijenti</a>
        <a routerLink="/portal/orders" *ngIf="isClient()">Moji nalozi</a>
        <a routerLink="/portal/locations" *ngIf="isClient()">Lokacije</a>
      </div>
      <div class="navbar-user">
        <span>{{ userName() }} ({{ roleLabel() }})</span>
        <button class="btn-logout" (click)="logout()">Odjava</button>
      </div>
    </nav>
    <main [class.with-nav]="isLoggedIn()">
      <router-outlet />
    </main>
  `,
  styleUrl: './app.component.scss',
})
export class AppComponent {
  isLoggedIn = computed(() => !!this.auth.currentUser());
  isAdmin    = computed(() => this.auth.hasRole('ADMIN'));
  isOffice   = computed(() => this.auth.hasRole('OFFICE'));
  isServicer = computed(() => this.auth.hasRole('SERVICER'));
  isEmployee = computed(() => this.auth.hasRole('ADMIN', 'OFFICE', 'SERVICER'));
  isClient   = computed(() => this.auth.hasRole('CLIENT'));
  userName   = computed(() => this.auth.currentUser()?.displayName ?? '');
  roleLabel  = computed(() => {
    const role = this.auth.currentUser()?.role;
    return role ? ROLE_LABELS[role] : '';
  });

  constructor(private auth: AuthService) {}

  logout() { this.auth.logout(); }
}
