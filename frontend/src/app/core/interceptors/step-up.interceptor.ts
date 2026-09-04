import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { catchError, switchMap, throwError } from 'rxjs';
import { StepUpService } from '../services/step-up.service';

/** Guards against retry loops — a request is only ever auto-retried once. */
const STEP_UP_RETRIED = new HttpContextToken<boolean>(() => false);

/**
 * Catches the 403 body StepUpAuthFilter returns on high-risk admin mutations
 * (invoices, payments, pricing rules, MFA disable/force-reset, sessions) when the
 * account's MFA assertion has gone stale, prompts for the current TOTP code via
 * StepUpService's modal, then transparently retries the original request. The
 * caller (e.g. a form's save() handler) never sees the 403 unless the user cancels
 * or the retry itself is challenged again.
 */
export const stepUpInterceptor: HttpInterceptorFn = (req, next) => {
  const platformId = inject(PLATFORM_ID);
  if (!isPlatformBrowser(platformId)) {
    return next(req);
  }

  const stepUpService = inject(StepUpService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 403 &&
        isStepUpChallenge(error) &&
        !req.context.get(STEP_UP_RETRIED)
      ) {
        return stepUpService.challenge().pipe(
          switchMap(() => next(req.clone({ context: req.context.set(STEP_UP_RETRIED, true) }))),
          // Cancelled by the user, or the retry failed too — surface the original error either way.
          catchError(() => throwError(() => error)),
        );
      }
      return throwError(() => error);
    }),
  );
};

function isStepUpChallenge(error: HttpErrorResponse): boolean {
  const body = error.error;
  if (!body || typeof body !== 'object') return false;
  if (body.error === 'Step-up required') return true;
  return typeof body.message === 'string' && body.message.toLowerCase().includes('step-up');
}
