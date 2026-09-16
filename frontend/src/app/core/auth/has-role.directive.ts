import {
  Directive,
  effect,
  inject,
  Input,
  signal,
  TemplateRef,
  ViewContainerRef,
} from '@angular/core';

import { Role } from '../models/user.model';
import { AuthService } from './auth.service';

/**
 * Structural directive that renders its content only for the given roles:
 *
 *   <button *appHasRole="['ADMIN']">Manage doctors</button>
 *   <a *appHasRole="['DOCTOR', 'ADMIN']">Schedule</a>
 *
 * Structural (not [hidden]/CSS) so the element is genuinely absent from the
 * DOM rather than merely invisible — hiding a control with CSS still ships it
 * to the page for anyone who opens devtools.
 *
 * Like the guards, this is presentation only. It stops a patient seeing an
 * admin button; it is not what stops them calling the admin endpoint. That
 * is @PreAuthorize on the server.
 *
 * The roles are held in a signal so the effect re-runs when either the roles
 * or the logged-in user changes. An effect rather than a subscription means
 * there is nothing to unsubscribe — it is torn down with the directive.
 */
@Directive({
  selector: '[appHasRole]',
  standalone: true,
})
export class HasRoleDirective {
  private readonly auth = inject(AuthService);
  private readonly templateRef = inject(TemplateRef<unknown>);
  private readonly viewContainer = inject(ViewContainerRef);

  private readonly allowed = signal<Role[]>([]);
  private rendered = false;

  constructor() {
    effect(() => {
      const permitted = this.auth.hasRole(...this.allowed());

      if (permitted && !this.rendered) {
        this.viewContainer.createEmbeddedView(this.templateRef);
        this.rendered = true;
      } else if (!permitted && this.rendered) {
        this.viewContainer.clear();
        this.rendered = false;
      }
    });
  }

  @Input({ required: true })
  set appHasRole(roles: Role[]) {
    this.allowed.set(roles);
  }
}
