import { Component, OnInit, inject, signal } from "@angular/core";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { AuthService } from "../../../core/services/auth.service";
import { SeoService } from "../../../core/services/seo.service";

@Component({
  selector: "app-accept-invitation",
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: "./accept-invitation.component.html",
  styleUrl: "./accept-invitation.component.scss",
})
export class AcceptInvitationComponent implements OnInit {
  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(AuthService);
  private seo = inject(SeoService);

  loading = signal(false);
  success = signal(false);
  errorMessage = signal<string | null>(null);
  private token = "";
  isAdminInvitation = false;

  form = this.fb.nonNullable.group({
    fullName: [""],
    password: ["", [Validators.required, Validators.minLength(8)]],
  });

  ngOnInit(): void {
    this.seo.update({
      title: "Set up your workspace",
      description: "Activate your Neelastack client project workspace.",
      noindex: true,
    });

    this.token = this.route.snapshot.queryParamMap.get("token") ?? "";
    this.isAdminInvitation = this.router.url.startsWith("/accept-admin-invitation");
    this.seo.update({ title: this.isAdminInvitation ? "Accept admin invitation" : "Set up your workspace", description: this.isAdminInvitation ? "Activate your Neelastack administrator account." : "Activate your Neelastack client project workspace.", noindex: true });
    if (!this.token) {
      this.errorMessage.set(
        "This invitation link is missing its token. Please check the email again or ask us to resend it.",
      );
    }
  }

  submit(): void {
    if (this.form.invalid || !this.token) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const { password, fullName } = this.form.getRawValue();

    this.authService.acceptInvitation(this.token, password, fullName || undefined).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(true);
        setTimeout(() => this.router.navigate([this.isAdminInvitation ? "/admin" : "/dashboard"]), 1200);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(
          err?.error?.message ?? "This invitation link is invalid or has expired.",
        );
      },
    });
  }
}
