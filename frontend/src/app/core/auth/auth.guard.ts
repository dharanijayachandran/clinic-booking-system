import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Role } from '../models/user.model';
import { AuthService } from './auth.service';

/**
 * Guards are a UX affordance, never a security boundary. They stop a user
 * navigating somewhere pointless; they do not protect data. Every one of
 * these routes is backed by server-side authorization (@PreAuthorize and the
 * Spring Security filter chain), because anything enforced only in the
 * browser is enforced nowhere — the user controls the browser.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/login'], { queryParams: { redirectTo: state.url } });
};

/** Route-level role check, e.g. canActivate: [authGuard, roleGuard('ADMIN')]. */
export const roleGuard = (...allowed: Role[]): CanActivateFn => {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (auth.hasRole(...allowed)) {
      return true;
    }

    // Authenticated but wrong role: send them somewhere they can actually be,
    // rather than bouncing to login, which would just loop.
    return router.createUrlTree(['/forbidden']);
  };
};

/** Keeps logged-in users off the login/register pages. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.isAuthenticated() ? router.createUrlTree(['/']) : true;
};
