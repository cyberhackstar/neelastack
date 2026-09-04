import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { PLATFORM_ID } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthResponse } from '../models/user.model';

const ACCESS_TOKEN_KEY = 'neelastack_access_token';
const REFRESH_TOKEN_KEY = 'neelastack_refresh_token';
const USER_KEY = 'neelastack_user';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;

  const authResponse: AuthResponse = {
    accessToken: 'access-123',
    refreshToken: 'refresh-456',
    tokenType: 'Bearer',
    fullName: 'Jane Client',
    email: 'jane@example.com',
    role: 'CLIENT',
    emailVerified: false,
  };

  function clearStorage() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  beforeEach(() => {
    clearStorage();
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'browser' },
        { provide: Router, useValue: routerSpy },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    clearStorage();
  });

  it('is not authenticated and has no current user before any session exists', () => {
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.currentUser()).toBeNull();
  });

  it('persists tokens and the user to localStorage on successful login', () => {
    service.login({ email: authResponse.email, password: 'irrelevant' }).subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush(authResponse);

    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBe('access-123');
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('refresh-456');
    expect(service.isAuthenticated()).toBeTrue();
    expect(service.currentUser()?.email).toBe('jane@example.com');
  });

  it('persists tokens on successful registration the same way as login', () => {
    service
      .register({ fullName: 'Jane Client', email: authResponse.email, password: 'irrelevant' })
      .subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/auth/register');
    req.flush(authResponse);

    expect(service.isAuthenticated()).toBeTrue();
  });

  it('clears the session and redirects to /login on logout, even if server revocation fails', () => {
    // Establish a real session the normal way (via login()) rather than seeding
    // localStorage directly, since AuthService only reads stored state once, at
    // construction — seeding storage after the service already exists wouldn't
    // update its in-memory signal.
    service.login({ email: authResponse.email, password: 'irrelevant' }).subscribe();
    httpMock.expectOne('http://localhost:8080/api/v1/auth/login').flush(authResponse);
    expect(service.isAuthenticated()).toBeTrue();

    service.logout();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/auth/logout');
    req.flush(null, { status: 500, statusText: 'Server Error' });

    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('logs out locally without a network call when there is no refresh token to revoke', () => {
    service.logout();

    httpMock.expectNone('http://localhost:8080/api/v1/auth/logout');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });
});
