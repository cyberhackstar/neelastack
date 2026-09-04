import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { SeoService } from '../../../core/services/seo.service';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './verify-email.component.html',
  styleUrl: './verify-email.component.scss',
})
export class VerifyEmailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private authService = inject(AuthService);
  private seo = inject(SeoService);

  status = signal<'verifying' | 'success' | 'error'>('verifying');
  errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.seo.update({ title: 'Verify Email', description: 'Confirm your Neelastack email address.', noindex: true });

    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.status.set('error');
      this.errorMessage.set('This verification link is missing its token.');
      return;
    }

    this.authService.verifyEmail(token).subscribe({
      next: () => {
        this.status.set('success');
        this.authService.markEmailVerifiedLocally();
      },
      error: (err) => {
        this.status.set('error');
        this.errorMessage.set(err?.error?.message ?? 'This link is invalid or has expired.');
      },
    });
  }
}
