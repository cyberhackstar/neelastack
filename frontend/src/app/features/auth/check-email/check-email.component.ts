import { Component, OnInit, inject, signal } from "@angular/core";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { AuthService } from "../../../core/services/auth.service";
import { SeoService } from "../../../core/services/seo.service";
import { LogoComponent } from "../../../shared/components/logo/logo.component";

/**
 * Landing step after /register (security review P1 #1: registration no longer signs the
 * user in directly, so there's somewhere to send them besides the dashboard). Purely
 * informational plus a resend affordance — the actual verification happens when the user
 * clicks the link in their email and lands on /verify-email.
 */
@Component({
  selector: "app-check-email",
  standalone: true,
  imports: [RouterLink],
  templateUrl: "./check-email.component.html",
  styleUrl: "./check-email.component.scss",
})
export class CheckEmailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private authService = inject(AuthService);
  private seo = inject(SeoService);

  email = signal<string | null>(null);
  resending = signal(false);
  resent = signal(false);

  ngOnInit(): void {
    this.seo.update({
      title: "Check your email",
      description: "Confirm your Neelastack email address to finish creating your account.",
      noindex: true,
    });

    this.email.set(this.route.snapshot.queryParamMap.get("email"));
  }

  resend(): void {
    const email = this.email();
    if (!email || this.resending()) return;

    this.resending.set(true);
    this.authService.resendVerification(email).subscribe({
      // Backend always responds 202 regardless of whether the email exists or is already
      // verified (anti-enumeration) — treat any completion, success or error, the same way.
      next: () => {
        this.resending.set(false);
        this.resent.set(true);
      },
      error: () => {
        this.resending.set(false);
        this.resent.set(true);
      },
    });
  }
}
