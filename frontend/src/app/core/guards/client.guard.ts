import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { isAdminRole } from '../models/user.model';

/**
 * Client-only workspace guard. Admin users have a separate admin workspace and
 * should never be routed into the client /dashboard pages, where client actions
 * such as invoice payment are exposed.
 */
export const clientGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const user = authService.currentUser();

  if (authService.isAuthenticated() && user && !isAdminRole(user.role)) {
    return true;
  }

  if (user && isAdminRole(user.role)) {
    router.navigate(['/admin']);
  } else {
    router.navigate(['/login']);
  }

  return false;
};
