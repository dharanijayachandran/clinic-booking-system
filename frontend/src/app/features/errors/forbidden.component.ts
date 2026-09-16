import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule, RouterLink],
  template: `
    <mat-card>
      <mat-card-header><mat-card-title>Not available for your role</mat-card-title></mat-card-header>
      <mat-card-content>
        <p>You're signed in, but this area belongs to a different role.</p>
        <a routerLink="/">Back to home</a>
      </mat-card-content>
    </mat-card>
  `,
})
export class ForbiddenComponent {}
