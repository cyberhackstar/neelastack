import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { adminGuard } from './admin.guard';
import { AuthService } from '../services/auth.service';
import { AuthResponse } from '../models/user.model';

describe('adminGuard', () => {
  let routerSpy: jasmine.SpyObj<Router>;
  let currentUserValue: AuthResponse | null;

  const adminUser: AuthResponse = {
    accessToken: 'a',
    refreshToken: 'r',
    tokenType: 'Bearer',
    fullName: 'Admin User',
    email: 'admin@example.com',
    role: 'ADMIN',
    emailVerified: true,
  };

  const clientUser: AuthResponse = { ...adminUser, role: 'CLIENT', email: 'client@example.com' };

  beforeEach(() => {
    currentUserValue = null;
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    // AuthService.currentUser is a signal (a callable, not a plain field), so the mock
    // needs to be an actual function reference rather than a jasmine property spy.
    const authServiceMock = { currentUser: () => currentUserValue };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  function runGuard() {
    return TestBed.runInInjectionContext(() => adminGuard({} as any, {} as any));
  }

  it('allows navigation for a user with the ADMIN role', () => {
    currentUserValue = adminUser;

    const result = runGuard();

    expect(result).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('blocks navigation and redirects to /login for a CLIENT-role user', () => {
    currentUserValue = clientUser;

    const result = runGuard();

    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('blocks navigation when there is no logged-in user at all', () => {
    currentUserValue = null;

    const result = runGuard();

    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });
});
