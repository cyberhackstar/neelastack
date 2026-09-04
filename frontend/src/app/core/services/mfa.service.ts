import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { MfaSetupResponse, MfaStatus, MfaVerifyResponse } from '../models/user.model';

/**
 * TOTP-based MFA for the calling admin's own account — mirrors backend MfaController
 * one-to-one (see /admin/mfa/**, requires ROLE_ADMIN). `stepUp` is also called
 * directly by StepUpService when a mutation is challenged with 403 Step-up required.
 */
@Injectable({ providedIn: 'root' })
export class MfaService {
  private http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/admin/mfa`;

  status() {
    return this.http.get<MfaStatus>(`${this.baseUrl}/status`);
  }

  /** Begins enrollment — returns a QR code + manual-entry secret. mfaEnabled stays false until verify() succeeds. */
  setup() {
    return this.http.post<MfaSetupResponse>(`${this.baseUrl}/setup`, {});
  }

  /** Completes enrollment with a 6-digit code from the authenticator app. Recovery codes are returned once, here only. */
  verify(code: string) {
    return this.http.post<MfaVerifyResponse>(`${this.baseUrl}/verify`, { code });
  }

  /** Disabling requires a fresh password AND a current TOTP code — not just a valid session. */
  disable(password: string, code: string) {
    return this.http.post<void>(`${this.baseUrl}/disable`, { password, code });
  }

  /** Consumes one single-use recovery code — used when the authenticator device is unavailable. */
  recovery(recoveryCode: string) {
    return this.http.post<void>(`${this.baseUrl}/recovery`, { recoveryCode });
  }

  /** Refreshes the step-up assertion checked on high-risk admin mutations (invoices, payments, pricing rules, sessions, MFA disable/force-reset). */
  stepUp(code: string) {
    return this.http.post<void>(`${this.baseUrl}/step-up`, { code });
  }
}
