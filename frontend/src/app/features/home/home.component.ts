import { Component, computed } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { ROLE_LABELS } from '../../core/labels';

// The page after login. Roadmap step 8 turns it into the dashboard of each role.
@Component({
  selector: 'app-home',
  standalone: true,
  template: `
    @if (user(); as user) {
      <div class="card">
        <h2>Dobrodošli, {{ user.displayName }}</h2>
        <p>Prijavljeni ste kao: {{ roleLabel() }}</p>
      </div>
    }
  `,
})
export class HomeComponent {
  user = computed(() => this.auth.currentUser());
  roleLabel = computed(() => {
    const role = this.auth.currentUser()?.role;
    return role ? ROLE_LABELS[role] : '';
  });

  constructor(private auth: AuthService) {}
}
