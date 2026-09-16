import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth/auth.service';
import { HasRoleDirective } from '../core/auth/has-role.directive';

@Component({
  selector: 'app-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    HasRoleDirective,
  ],
  template: `
    <mat-toolbar color="primary" class="shell-toolbar">
      <a routerLink="/" class="brand">Clinic</a>

      @if (auth.isAuthenticated()) {
        <nav class="links">
          <a mat-button routerLink="/book" routerLinkActive="active" *appHasRole="['PATIENT']">
            Book
          </a>
          <a mat-button routerLink="/appointments" routerLinkActive="active" *appHasRole="['PATIENT']">
            My appointments
          </a>
          <a mat-button routerLink="/schedule" routerLinkActive="active" *appHasRole="['DOCTOR']">
            My schedule
          </a>
          <a mat-button routerLink="/admin" routerLinkActive="active" *appHasRole="['ADMIN']">
            Admin
          </a>
        </nav>
      }

      <span class="spacer"></span>

      @if (auth.isAuthenticated()) {
        <span class="who">{{ auth.currentUser()?.firstName }} ({{ auth.role() }})</span>
        <button mat-button (click)="logout()">Sign out</button>
      } @else {
        <a mat-button routerLink="/login">Sign in</a>
        <a mat-button routerLink="/register">Register</a>
      }
    </mat-toolbar>

    <main class="content">
      <router-outlet />
    </main>
  `,
  styles: [
    `
      .shell-toolbar {
        gap: 0.5rem;
      }
      .brand {
        font-weight: 700;
        text-decoration: none;
        color: inherit;
      }
      .links {
        display: flex;
        gap: 0.25rem;
        margin-left: 1rem;
      }
      .spacer {
        flex: 1 1 auto;
      }
      .who {
        font-size: 0.85rem;
        opacity: 0.9;
      }
      .content {
        padding: 1.5rem;
        max-width: 72rem;
        margin: 0 auto;
      }
      .active {
        text-decoration: underline;
      }
    `,
  ],
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  logout(): void {
    // No takeUntilDestroyed needed: HttpClient completes after one emission,
    // so this subscription ends on its own rather than leaking.
    this.auth.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login']),
    });
  }
}
