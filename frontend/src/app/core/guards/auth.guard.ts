import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models/models';
import { AuthService } from '../services/auth.service';

// Pages behind the login. Without a logged-in user, go to the login page.
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.currentUser()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login']);
};

// Pages for some roles only, the same roles the backend allows. Anyone else goes to their start page.
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    if (auth.hasRole(...roles)) {
      return true;
    }
    return inject(Router).createUrlTree(['/']);
  };
}

// "/" has no page of its own: every role starts on its main screen
export const startPageGuard: CanActivateFn = () => {
  const role = inject(AuthService).currentUser()?.role;
  return inject(Router).createUrlTree([startPageOf(role)]);
};

function startPageOf(role: Role | undefined): string {
  switch (role) {
    case 'SERVICER':
      return '/servicer';
    case 'CLIENT':
      return '/portal/orders';
    default:
      return '/orders';
  }
}
