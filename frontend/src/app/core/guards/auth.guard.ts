import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

// Pages behind the login. Without a logged-in user, go to the login page.
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.currentUser()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login']);
};
