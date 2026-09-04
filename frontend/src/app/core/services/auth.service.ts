import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { tap, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginPayload, RegisterPayload } from '../models/user.model';

const ACCESS_TOKEN_KEY = 'neelastack_access_token';
const REFRESH_TOKEN_KEY = 'neelastack_refresh_token';
const USER_KEY = 'neelastack_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  private readonly apiUrl = `${environment.apiBaseUrl}/auth`;

  readonly currentUser = signal<AuthResponse | null>(this.readStoredUser());

  register(payload: RegisterPayload) {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/register`, payload)
      .pipe(tap((res) => this.persistSession(res)));
  }

  login(payload: LoginPayload) {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/login`, payload)
      .pipe(tap((res) => this.persistSessionUnlessChallenged(res)));
  }

  /** Exchanges the mfaToken from a challenged login() response, plus a TOTP or recovery code, for real tokens. */
  loginMfa(mfaToken: string, code: string, useRecoveryCode = false) {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/login/mfa`, { mfaToken, code, useRecoveryCode })
      .pipe(tap((res) => this.persistSession(res)));
  }

  /**
   * Self-service password change for the current account — the path a freshly-bootstrapped
   * admin (mustChangePassword=true, see AdminBootstrapRunner) or a force-reset account uses.
   * Requires the current password. Re-persists the session since the backend invalidates
   * every previously-issued token as part of the change and hands back a fresh pair.
   */
  changePassword(currentPassword: string, newPassword: string) {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/change-password`, { currentPassword, newPassword })
      .pipe(tap((res) => this.persistSession(res)));
  }

  logout(): void {
    const refreshToken = this.getRefreshToken();

    const clearAndRedirect = () => {
      if (this.isBrowser) {
        localStorage.removeItem(ACCESS_TOKEN_KEY);
        localStorage.removeItem(REFRESH_TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
      }
      this.currentUser.set(null);
      this.router.navigate(['/login']);
    };

    if (refreshToken) {
      // Best-effort server-side revocation — logout still proceeds locally even if this fails.
      this.http
        .post(`${this.apiUrl}/logout`, { refreshToken })
        .pipe(catchError(() => of(null)))
        .subscribe(() => clearAndRedirect());
    } else {
      clearAndRedirect();
    }
  }

  forgotPassword(email: string) {
    return this.http.post<void>(`${this.apiUrl}/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string) {
    return this.http.post<void>(`${this.apiUrl}/reset-password`, { token, newPassword });
  }

  verifyEmail(token: string) {
    return this.http.post<void>(`${this.apiUrl}/verify-email`, { token });
  }

  resendVerification(email: string) {
    return this.http.post<void>(`${this.apiUrl}/resend-verification`, { email });
  }

  exchangeOAuthCode(code: string) {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/oauth-exchange`, { code })
      .pipe(tap((res) => this.persistSession(res)));
  }

  /** Optimistically marks the in-memory + stored user as verified after a successful verify-email call. */
  markEmailVerifiedLocally(): void {
    const user = this.currentUser();
    if (!user) return;
    const updated = { ...user, emailVerified: true };
    this.currentUser.set(updated);
    if (this.isBrowser) {
      localStorage.setItem(USER_KEY, JSON.stringify(updated));
    }
  }

  getAccessToken(): string | null {
    return this.isBrowser ? localStorage.getItem(ACCESS_TOKEN_KEY) : null;
  }

  getRefreshToken(): string | null {
    return this.isBrowser ? localStorage.getItem(REFRESH_TOKEN_KEY) : null;
  }

  isAuthenticated(): boolean {
    return !!this.getAccessToken();
  }

  private persistSessionUnlessChallenged(res: AuthResponse): void {
    if (res.mfaRequired) return; // no tokens yet — LoginComponent prompts for the code and calls loginMfa() instead
    this.persistSession(res);
  }

  private persistSession(res: AuthResponse): void {
    if (this.isBrowser) {
      localStorage.setItem(ACCESS_TOKEN_KEY, res.accessToken);
      localStorage.setItem(REFRESH_TOKEN_KEY, res.refreshToken);
      localStorage.setItem(USER_KEY, JSON.stringify(res));
    }
    this.currentUser.set(res);
  }

  private readStoredUser(): AuthResponse | null {
    if (!this.isBrowser) return null;
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as AuthResponse;
    } catch {
      // Corrupted/stale localStorage value must not crash app bootstrap — drop the bad
      // session and continue as logged-out rather than throwing during service init.
      localStorage.removeItem(USER_KEY);
      localStorage.removeItem(ACCESS_TOKEN_KEY);
      localStorage.removeItem(REFRESH_TOKEN_KEY);
      return null;
    }
  }
}

