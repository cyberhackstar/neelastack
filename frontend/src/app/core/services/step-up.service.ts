import { Injectable, inject, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { MfaService } from './mfa.service';

/**
 * Bridges the step-up interceptor (which catches 403 "Step-up required" responses
 * from StepUpAuthFilter on high-risk admin mutations) to a single global modal,
 * mounted once in AppComponent alongside the other root-level UI (see
 * VerifyBannerComponent for the same pattern). Concurrent 403s share one prompt —
 * a second caller just gets the same pending Observable instead of stacking modals.
 */
@Injectable({ providedIn: 'root' })
export class StepUpService {
  private mfa = inject(MfaService);

  readonly visible = signal(false);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  private pending: Subject<void> | null = null;

  /** Called by the interceptor when a request is challenged. Emits once the user verifies; errors if they cancel. */
  challenge(): Observable<void> {
    if (!this.pending) {
      this.pending = new Subject<void>();
      this.errorMessage.set(null);
      this.visible.set(true);
    }
    return this.pending.asObservable();
  }

  /** Called by the modal on submit. */
  submit(code: string): void {
    if (!this.pending) return;
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.mfa.stepUp(code).subscribe({
      next: () => {
        this.submitting.set(false);
        this.visible.set(false);
        this.pending?.next();
        this.pending?.complete();
        this.pending = null;
      },
      error: () => {
        this.submitting.set(false);
        this.errorMessage.set('That code was incorrect or has expired. Try the current code from your authenticator app.');
      },
    });
  }

  /** Called by the modal's cancel button, or if the user navigates away. */
  cancel(): void {
    this.visible.set(false);
    this.submitting.set(false);
    this.pending?.error(new Error('Step-up verification was cancelled.'));
    this.pending = null;
  }
}
