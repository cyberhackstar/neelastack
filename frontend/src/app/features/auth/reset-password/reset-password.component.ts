import { Component, OnInit, inject, signal } from "@angular/core";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { AuthService } from "../../../core/services/auth.service";
import { SeoService } from "../../../core/services/seo.service";
import { LogoComponent } from "../../../shared/components/logo/logo.component";

@Component({
  selector: "app-reset-password",
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: "./reset-password.component.html",
  styleUrl: "./reset-password.component.scss",
})
export class ResetPasswordComponent implements OnInit {
  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(AuthService);
  private seo = inject(SeoService);

  loading = signal(false);
  success = signal(false);
  errorMessage = signal<string | null>(null);
  private token = "";

  form = this.fb.nonNullable.group({
    newPassword: ["", [Validators.required, Validators.minLength(8)]],
  });

  ngOnInit(): void {
    this.seo.update({
      title: "Reset Password",
      description: "Set a new password for your Neelastack account.",
      noindex: true,
    });

    this.token = this.route.snapshot.queryParamMap.get("token") ?? "";
    if (!this.token) {
      this.errorMessage.set(
        "This reset link is missing its token. Please request a new one.",
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

    this.authService
      .resetPassword(this.token, this.form.getRawValue().newPassword)
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.success.set(true);
          setTimeout(() => this.router.navigate(["/login"]), 2500);
        },
        error: (err) => {
          this.loading.set(false);
          this.errorMessage.set(
            err?.error?.message ?? "This link is invalid or has expired.",
          );
        },
      });
  }
}
