/**
 * SUPERADMIN is a superset of ADMIN (see backend User#getAuthorities()) — it's granted
 * everything a plain ADMIN can do, plus a small set of superadmin-only operations (e.g. MFA
 * force-reset). Any UI check that gates on "is this an admin" should treat SUPERADMIN the
 * same as ADMIN — see isAdminRole() below — rather than comparing against 'ADMIN' alone.
 */
export type UserRole = 'SUPERADMIN' | 'ADMIN' | 'CLIENT';

/** True for both ADMIN and SUPERADMIN — use this instead of `role === 'ADMIN'` for any
 *  "can this user see/do admin things" check. */
export function isAdminRole(role: UserRole | undefined | null): boolean {
  return role === 'ADMIN' || role === 'SUPERADMIN';
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  fullName: string;
  email: string;
  role: UserRole;
  emailVerified: boolean;
  /** True when the account has MFA enabled — accessToken/refreshToken are absent and
   *  mfaToken must be exchanged via AuthService.completeMfaLogin() instead. */
  mfaRequired: boolean;
  mfaToken: string | null;
  /** True when this account must call AuthService.changePassword() before anything else will
   *  succeed — the freshly-bootstrapped admin, or any account an admin has force-reset. */
  mustChangePassword: boolean;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
  phone?: string;
}

/** Mirrors backend MfaStatusResponse. */
export interface MfaStatus {
  mfaEnabled: boolean;
  mfaEnrolledAt: string | null;
  remainingRecoveryCodes: number;
}

/** Mirrors backend MfaSetupResponse — qrCodeDataUri is a ready-to-render data: URI. */
export interface MfaSetupResponse {
  manualEntrySecret: string;
  qrCodeDataUri: string;
}

/** Mirrors backend MfaVerifyResponse — recoveryCodes is populated once, only on the call that completes enrollment. */
export interface MfaVerifyResponse {
  mfaEnabled: boolean;
  recoveryCodes: string[];
}
