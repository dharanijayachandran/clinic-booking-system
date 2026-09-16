import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { APP_INITIALIZER, ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { catchError, of } from 'rxjs';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';

/**
 * Ask the server who we are before the first route resolves.
 *
 * The auth cookies survive a page refresh, but the in-memory signal doesn't —
 * without this, every reload would look like a logout and the auth guard
 * would bounce a perfectly valid session to /login. A 401 here is the normal
 * "nobody is signed in" case, so it's swallowed rather than treated as an
 * error; the app simply starts logged out.
 */
function restoreSession(auth: AuthService) {
  return () =>
    auth.loadCurrentUser().pipe(
      catchError(() => {
        auth.markResolved();
        return of(null);
      }),
    );
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideAnimationsAsync(),
    // Same-origin via the dev proxy, which is what makes Angular's built-in
    // XSRF interceptor work: it reads the XSRF-TOKEN cookie Spring sets and
    // echoes it in X-XSRF-TOKEN. Cross-origin it would silently skip, since
    // JS can't read another origin's cookie — hence proxy.conf.json.
    provideHttpClient(
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
    ),
    {
      provide: APP_INITIALIZER,
      useFactory: restoreSession,
      deps: [AuthService],
      multi: true,
    },
  ],
};
