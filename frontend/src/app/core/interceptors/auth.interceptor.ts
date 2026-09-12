import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { API_URL } from '../api';
import { AuthService } from '../services/auth.service';

const LOGIN_URL = `${API_URL}/auth/login`;

// Adds the token to every API call.
// A 401 means the token expired or the user was deactivated, so the user has to log in again.
// The login call itself is left out: there a 401 just means a wrong username or password.
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const token = auth.getToken();
  const withToken = token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;

  return next(withToken).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && request.url !== LOGIN_URL) {
        auth.logout();
      }
      return throwError(() => error);
    }),
  );
};
