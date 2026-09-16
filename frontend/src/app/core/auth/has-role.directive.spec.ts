import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Role, User } from '../models/user.model';
import { AuthService } from './auth.service';
import { HasRoleDirective } from './has-role.directive';

@Component({
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [HasRoleDirective],
  template: `<button *appHasRole="['ADMIN']" id="admin-only">Manage doctors</button>`,
})
class HostComponent {}

describe('HasRoleDirective', () => {
  let fixture: ComponentFixture<HostComponent>;
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HostComponent);
  });

  afterEach(() => http.verify());

  function signIn(role: Role): void {
    const user: User = { id: 'u-1', email: 'x@example.com', firstName: 'X', lastName: 'Y', role };
    auth.login({ email: user.email, password: 'pw' }).subscribe();
    http.expectOne('/api/auth/login').flush(user);
  }

  function adminButton(): HTMLElement | null {
    return fixture.nativeElement.querySelector('#admin-only');
  }

  it('renders nothing for an anonymous visitor', () => {
    fixture.detectChanges();
    expect(adminButton()).toBeNull();
  });

  it('renders nothing for the wrong role', () => {
    signIn('PATIENT');
    fixture.detectChanges();
    expect(adminButton()).toBeNull();
  });

  it('renders for a permitted role', () => {
    signIn('ADMIN');
    fixture.detectChanges();
    expect(adminButton()).not.toBeNull();
  });

  it('removes the element from the DOM entirely, not just visually', () => {
    signIn('ADMIN');
    fixture.detectChanges();
    expect(adminButton()).not.toBeNull();

    auth.logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null);
    fixture.detectChanges();

    // Not "hidden" — actually absent, so it never ships to the page.
    expect(adminButton()).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Manage doctors');
  });
});
