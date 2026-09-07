import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { isAdminRole } from '../models/user.model';

export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const user = authService.currentUser();

  if (user && isAdminRole(user.role)) {
    return true;
  }

  router.navigate(['/login']);
  return false;
};
