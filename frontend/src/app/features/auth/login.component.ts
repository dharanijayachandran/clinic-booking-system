import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  template: `
    <mat-card class="auth-card">
      <mat-card-header><mat-card-title>Sign in</mat-card-title></mat-card-header>
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field appearance="outline">
            <mat-label>Email</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="email" />
            @if (form.controls.email.touched && form.controls.email.invalid) {
              <mat-error>Enter a valid email address.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Password</mat-label>
            <input matInput type="password" formControlName="password" autocomplete="current-password" />
            @if (form.controls.password.touched && form.controls.password.invalid) {
              <mat-error>Password is required.</mat-error>
            }
          </mat-form-field>

          @if (error()) {
            <p class="error">{{ error() }}</p>
          }

          <button mat-flat-button color="primary" type="submit" [disabled]="submitting()">
            {{ submitting() ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>
      </mat-card-content>
      <mat-card-actions>
        <a routerLink="/register">Need an account? Register</a>
      </mat-card-actions>
    </mat-card>
  `,
  styles: [
    `
      .auth-card {
        max-width: 26rem;
        margin: 2rem auto;
      }
      form {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
      .error {
        color: #b3261e;
        margin: 0 0 0.5rem;
      }
    `,
  ],
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        const redirectTo = this.route.snapshot.queryParamMap.get('redirectTo') ?? '/';
        this.router.navigateByUrl(redirectTo);
      },
      error: () => {
        // The API deliberately returns the same message for a wrong password
        // and an unknown email, so this can't be used to enumerate accounts.
        this.error.set('Invalid email or password.');
        this.submitting.set(false);
      },
    });
  }
}
