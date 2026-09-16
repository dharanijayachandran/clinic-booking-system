import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { Role, User } from '../models/user.model';
import { authGuard, guestGuard, roleGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('auth guards', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let router: Router;

  const route = {} as ActivatedRouteSnapshot;
  const state = { url: '/book' } as RouterStateSnapshot;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  function signIn(role: Role): void {
    const user: User = {
      id: 'u-1',
      email: 'x@example.com',
      firstName: 'X',
      lastName: 'Y',
      role,
    };
    auth.login({ email: user.email, password: 'pw' }).subscribe();
    http.expectOne('/api/auth/login').flush(user);
  }

  describe('authGuard', () => {
    it('lets a signed-in user through', () => {
      signIn('PATIENT');
      const result = TestBed.runInInjectionContext(() => authGuard(route, state));
      expect(result).toBe(true);
    });

    it('redirects an anonymous user to login, remembering where they were going', () => {
      const result = TestBed.runInInjectionContext(() => authGuard(route, state)) as UrlTree;

      expect(result).toBeInstanceOf(UrlTree);
      expect(router.serializeUrl(result)).toBe('/login?redirectTo=%2Fbook');
    });
  });

  describe('roleGuard', () => {
    it('allows a matching role', () => {
      signIn('ADMIN');
      const result = TestBed.runInInjectionContext(() => roleGuard('ADMIN')(route, state));
      expect(result).toBe(true);
    });

    it('allows any of several permitted roles', () => {
      signIn('DOCTOR');
      const result = TestBed.runInInjectionContext(() =>
        roleGuard('DOCTOR', 'ADMIN')(route, state),
      );
      expect(result).toBe(true);
    });

    it('sends a wrong-role user to /forbidden rather than looping through login', () => {
      signIn('PATIENT');
      const result = TestBed.runInInjectionContext(() =>
        roleGuard('ADMIN')(route, state),
      ) as UrlTree;

      expect(router.serializeUrl(result)).toBe('/forbidden');
    });
  });

  describe('guestGuard', () => {
    it('lets anonymous users reach login', () => {
      const result = TestBed.runInInjectionContext(() => guestGuard(route, state));
      expect(result).toBe(true);
    });

    it('bounces signed-in users away from login', () => {
      signIn('PATIENT');
      const result = TestBed.runInInjectionContext(() => guestGuard(route, state)) as UrlTree;
      expect(router.serializeUrl(result)).toBe('/');
    });
  });
});
