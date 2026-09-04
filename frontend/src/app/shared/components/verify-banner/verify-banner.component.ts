import { Component, inject, signal } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-verify-banner',
  standalone: true,
  templateUrl: './verify-banner.component.html',
  styleUrl: './verify-banner.component.scss',
})
export class VerifyBannerComponent {
  auth = inject(AuthService);

  dismissed = signal(false);
  sending = signal(false);
  sent = signal(false);

  resend(): void {
    const email = this.auth.currentUser()?.email;
    if (!email) return;
    this.sending.set(true);
    this.auth.resendVerification(email).subscribe({
      next: () => {
        this.sending.set(false);
        this.sent.set(true);
      },
      error: () => this.sending.set(false),
    });
  }
}
