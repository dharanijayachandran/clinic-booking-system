import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

import { AuthService } from '../../core/auth/auth.service';

/** Landing page after sign-in. Phase 5 replaces this with the calendar. */
@Component({
  selector: 'app-home',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title>Welcome, {{ auth.currentUser()?.firstName }}</mat-card-title>
        <mat-card-subtitle>Signed in as {{ auth.role() }}</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <p>The calendar and booking flow arrive in Phase 5.</p>
      </mat-card-content>
    </mat-card>
  `,
})
export class HomeComponent {
  protected readonly auth = inject(AuthService);
}
