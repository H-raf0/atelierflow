import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, Role } from './auth.service';

/** Signed-in users only; others are sent to the login page, which brings them back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.isAuthenticated()
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/**
 * Restricts a route to some roles. This is a comfort for the user (no page full of disabled
 * buttons): the API enforces the same rules and stays the actual security barrier.
 */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => inject(AuthService).hasRole(...roles) || inject(Router).createUrlTree(['/']);
}
