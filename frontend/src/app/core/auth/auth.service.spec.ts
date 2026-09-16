import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { User } from '../models/user.model';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  const patient: User = {
    id: 'u-1',
    email: 'patient@example.com',
    firstName: 'Pat',
    lastName: 'Ient',
    role: 'PATIENT',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts logged out and unresolved', () => {
    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.resolved()).toBe(false);
  });

  it('exposes the user as a signal after login', () => {
    service.login({ email: patient.email, password: 'pw' }).subscribe();

    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    request.flush(patient);

    expect(service.currentUser()).toEqual(patient);
    expect(service.isAuthenticated()).toBe(true);
    expect(service.role()).toBe('PATIENT');
  });

  it('never sends a token — the browser carries httpOnly cookies instead', () => {
    service.login({ email: patient.email, password: 'pw' }).subscribe();

    const request = http.expectOne('/api/auth/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(patient);
  });

  it('clears the user on logout', () => {
    service.login({ email: patient.email, password: 'pw' }).subscribe();
    http.expectOne('/api/auth/login').flush(patient);

    service.logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null);

    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('restores the session from /me', () => {
    service.loadCurrentUser().subscribe();
    http.expectOne('/api/auth/me').flush(patient);

    expect(service.currentUser()).toEqual(patient);
    expect(service.resolved()).toBe(true);
  });

  it('registers without sending a role — the API always creates a PATIENT', () => {
    service
      .register({ email: 'new@example.com', password: 'password1', firstName: 'A', lastName: 'B' })
      .subscribe();

    const request = http.expectOne('/api/auth/register');
    expect(request.request.body).not.toHaveProperty('role');
    request.flush(patient);

    // Registering does not sign you in.
    expect(service.isAuthenticated()).toBe(false);
  });

  describe('hasRole', () => {
    beforeEach(() => {
      service.login({ email: patient.email, password: 'pw' }).subscribe();
      http.expectOne('/api/auth/login').flush(patient);
    });

    it('matches the current role', () => {
      expect(service.hasRole('PATIENT')).toBe(true);
      expect(service.hasRole('DOCTOR', 'PATIENT')).toBe(true);
    });

    it('rejects other roles', () => {
      expect(service.hasRole('ADMIN')).toBe(false);
      expect(service.hasRole()).toBe(false);
    });
  });
});
