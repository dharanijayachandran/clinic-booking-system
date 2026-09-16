import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-register',
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
      <mat-card-header><mat-card-title>Create a patient account</mat-card-title></mat-card-header>
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field appearance="outline">
            <mat-label>First name</mat-label>
            <input matInput formControlName="firstName" autocomplete="given-name" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Last name</mat-label>
            <input matInput formControlName="lastName" autocomplete="family-name" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Email</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="email" />
            @if (form.controls.email.touched && form.controls.email.invalid) {
              <mat-error>Enter a valid email address.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Password</mat-label>
            <input matInput type="password" formControlName="password" autocomplete="new-password" />
            @if (form.controls.password.touched && form.controls.password.invalid) {
              <mat-error>At least 8 characters.</mat-error>
            }
          </mat-form-field>

          @if (error()) {
            <p class="error">{{ error() }}</p>
          }

          <button mat-flat-button color="primary" type="submit" [disabled]="submitting()">
            {{ submitting() ? 'Creating…' : 'Create account' }}
          </button>
        </form>
      </mat-card-content>
      <mat-card-actions>
        <a routerLink="/login">Already registered? Sign in</a>
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
export class RegisterComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    // Registration only ever creates a PATIENT — there is no role field to
    // send, by design. Doctors and admins are created by an admin.
    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => this.router.navigate(['/login'], { queryParams: { registered: '1' } }),
      error: (response: { status?: number }) => {
        this.error.set(
          response.status === 409
            ? 'That email is already registered.'
            : 'Could not create the account. Please try again.',
        );
        this.submitting.set(false);
      },
    });
  }
}
