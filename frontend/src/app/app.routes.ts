import { Routes } from '@angular/router';

import { authGuard, guestGuard, roleGuard } from './core/auth/auth.guard';
import { ShellComponent } from './layout/shell.component';

/**
 * Everything below the shell is lazily loaded with loadComponent, so a
 * visitor who only ever signs in never downloads the doctor or admin screens.
 */
export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      {
        path: 'login',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./features/auth/login.component').then((m) => m.LoginComponent),
      },
      {
        path: 'register',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./features/auth/register.component').then((m) => m.RegisterComponent),
      },
      {
        path: 'forbidden',
        loadComponent: () =>
          import('./features/errors/forbidden.component').then((m) => m.ForbiddenComponent),
      },
      {
        path: '',
        canActivate: [authGuard],
        loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
      },
      // Phase 5+ fills these in with real screens; the guard and nav wiring
      // is what Phase 4 is proving, so they point at the placeholder for now.
      {
        path: 'book',
        canActivate: [authGuard, roleGuard('PATIENT')],
        loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'appointments',
        canActivate: [authGuard, roleGuard('PATIENT')],
        loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'schedule',
        canActivate: [authGuard, roleGuard('DOCTOR')],
        loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'admin',
        canActivate: [authGuard, roleGuard('ADMIN')],
        loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
      },
      { path: '**', redirectTo: '' },
    ],
  },
];
