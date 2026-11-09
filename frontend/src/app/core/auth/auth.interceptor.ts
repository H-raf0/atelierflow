import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Endpoints authenticated by the refresh cookie (or not at all): no bearer token, no retry. */
const AUTH_ENDPOINTS = ['/api/auth/login', '/api/auth/refresh', '/api/auth/logout'];

function withToken(request: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
}

/**
 * Adds the access token to API calls. When the API answers 401 (token expired), the token is
 * refreshed once and the request replayed transparently; if the refresh fails too, the session is over.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/') || AUTH_ENDPOINTS.some((url) => request.url.startsWith(url))) {
    return next(request);
  }
  const auth = inject(AuthService);
  return next(withToken(request, auth.accessToken())).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        // placed before switchMap: only a failed refresh ends the session, not an error of the replay
        catchError(() => {
          auth.sessionExpired();
          return throwError(() => error);
        }),
        switchMap((token) => next(withToken(request, token))),
      );
    }),
  );
};
