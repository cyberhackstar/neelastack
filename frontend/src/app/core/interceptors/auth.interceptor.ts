import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { inject, PLATFORM_ID } from '@angular/core';
import {
  Observable,
  catchError,
  finalize,
  map,
  shareReplay,
  switchMap,
  throwError,
} from 'rxjs';
import { AuthService } from '../services/auth.service';

/** Prevent concurrent 401 responses from triggering multiple refresh-token rotations. */
let refreshInFlight$: Observable<string | null> | null = null;

const AUTH_PATHS = [
  '/auth/login',
  '/auth/login/mfa',
  '/auth/register',
  '/auth/refresh',
  '/auth/logout',
  '/auth/forgot-password',
  '/auth/reset-password',
  '/auth/verify-email',
  '/auth/resend-verification',
  '/auth/oauth-exchange',
  '/auth/change-password',
];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const platformId = inject(PLATFORM_ID);

  // Never access browser storage during SSR.
  if (!isPlatformBrowser(platformId)) {
    return next(req);
  }

  const authService = inject(AuthService);
  const isAuthEndpoint = AUTH_PATHS.some((path) => req.url.includes(path));
  const token = authService.getAccessToken();

  // Never forward an existing bearer token to authentication/session endpoints.
  const request = isAuthEndpoint || !token
    ? req
    : req.clone({
        setHeaders: { Authorization: `Bearer ${token}` },
      });

  return next(request).pipe(
    catchError((error: unknown) => {
      if (
        !(error instanceof HttpErrorResponse) ||
        error.status !== 401 ||
        isAuthEndpoint ||
        !authService.getRefreshToken()
      ) {
        return throwError(() => error);
      }

      if (!refreshInFlight$) {
        refreshInFlight$ = authService.refreshSession().pipe(
          map((response) => response?.accessToken ?? authService.getAccessToken()),
          catchError((refreshError: unknown) => {
            authService.clearLocalSession();
            return throwError(() => refreshError);
          }),
          finalize(() => {
            refreshInFlight$ = null;
          }),
          shareReplay({ bufferSize: 1, refCount: false }),
        );
      }

      return refreshInFlight$.pipe(
        switchMap((refreshedToken) => {
          if (!refreshedToken) {
            return throwError(() => error);
          }

          const retriedRequest = request.clone({
            setHeaders: { Authorization: `Bearer ${refreshedToken}` },
          });

          return next(retriedRequest);
        }),
      );
    }),
  );
};
