import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { LoginRequest, RegisterRequest, Role, User } from '../models/user.model';

/**
 * Auth state lives in signals, not a BehaviorSubject: components read
 * currentUser() directly in templates with no async pipe and no manual
 * subscription to leak, which is what keeps OnPush components correct by
 * default.
 *
 * Note there is no token handling anywhere in this service. The access and
 * refresh tokens are httpOnly cookies — deliberately unreadable by JavaScript,
 * so an XSS payload can't exfiltrate them. The browser attaches them
 * automatically; the frontend's only job is to know *who* is logged in, which
 * it learns from /api/auth/me.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly currentUserSignal = signal<User | null>(null);
  /** Distinguishes "not logged in" from "haven't asked the server yet". */
  private readonly resolvedSignal = signal(false);

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly resolved = this.resolvedSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null);
  readonly role = computed<Role | null>(() => this.currentUserSignal()?.role ?? null);

  login(request: LoginRequest): Observable<User> {
    return this.http
      .post<User>('/api/auth/login', request)
      .pipe(tap((user) => this.setUser(user)));
  }

  register(request: RegisterRequest): Observable<User> {
    return this.http.post<User>('/api/auth/register', request);
  }

  logout(): Observable<void> {
    return this.http
      .post<void>('/api/auth/logout', {})
      .pipe(tap(() => this.setUser(null)));
  }

  /**
   * Called once at startup (see provideAppInitializer in app.config.ts) so a
   * page refresh doesn't look like a logout: the cookies survive the reload,
   * so the session does too — the app just has to re-learn who the user is.
   */
  loadCurrentUser(): Observable<User> {
    return this.http.get<User>('/api/auth/me').pipe(tap((user) => this.setUser(user)));
  }

  hasRole(...roles: Role[]): boolean {
    const role = this.role();
    return role !== null && roles.includes(role);
  }

  private setUser(user: User | null): void {
    this.currentUserSignal.set(user);
    this.resolvedSignal.set(true);
  }

  markResolved(): void {
    this.resolvedSignal.set(true);
  }
}
