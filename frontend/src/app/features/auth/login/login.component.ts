import { Component, OnInit, inject, signal } from "@angular/core";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { AuthService } from "../../../core/services/auth.service";
import { SeoService } from "../../../core/services/seo.service";
import { GoogleSigninButtonComponent } from "../../../shared/components/google-signin-button/google-signin-button.component";
import { LogoComponent } from "../../../shared/components/logo/logo.component";

@Component({
  selector: "app-login",
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    GoogleSigninButtonComponent
  ],
  templateUrl: "./login.component.html",
  styleUrl: "./login.component.scss",
})
export class LoginComponent implements OnInit {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private seo = inject(SeoService);

  loading = signal(false);
  errorMessage = signal<string | null>(null);

  // Set once /login responds with mfaRequired — switches the template to the code-entry step.
  mfaToken = signal<string | null>(null);
  mfaSubmitting = signal(false);
  mfaError = signal<string | null>(null);
  useRecoveryCode = signal(false);

  form = this.fb.nonNullable.group({
    email: ["", [Validators.required, Validators.email]],
    password: ["", [Validators.required]],
  });

  mfaForm = this.fb.nonNullable.group({
    code: ["", [Validators.required]],
  });

  ngOnInit(): void {
    this.seo.update({
      title: "Sign In",
      description: "Sign in to your Neelastack account.",
      noindex: true,
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.login(this.form.getRawValue()).subscribe({
      next: (res) => {
        this.loading.set(false);
        if (res.mfaRequired && res.mfaToken) {
          this.mfaToken.set(res.mfaToken);
          return;
        }
        this.routeAfterLogin(res.mustChangePassword);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(
          err?.error?.message ?? "Unable to sign in. Please try again.",
        );
      },
    });
  }

  toggleRecoveryCode(): void {
    this.useRecoveryCode.set(!this.useRecoveryCode());
    this.mfaError.set(null);
    this.mfaForm.reset({ code: "" });
  }

  submitMfa(): void {
    const token = this.mfaToken();
    if (!token || this.mfaForm.invalid) {
      this.mfaForm.markAllAsTouched();
      return;
    }

    this.mfaSubmitting.set(true);
    this.mfaError.set(null);

    this.authService
      .loginMfa(token, this.mfaForm.controls.code.value, this.useRecoveryCode())
      .subscribe({
        next: (res) => {
          this.mfaSubmitting.set(false);
          this.routeAfterLogin(res.mustChangePassword);
        },
        error: (err) => {
          this.mfaSubmitting.set(false);
          this.mfaError.set(
            err?.error?.message ?? "That code did not work. Try again.",
          );
        },
      });
  }

  cancelMfa(): void {
    this.mfaToken.set(null);
    this.mfaError.set(null);
    this.mfaForm.reset({ code: "" });
  }

  /**
   * Every account with mustChangePassword=true (freshly-bootstrapped admin, or any
   * force-reset account — see MustChangePasswordFilter) must land on /change-password
   * instead of the app, since MustChangePasswordFilter rejects every other API call for
   * that account. Without this branch, login "succeeds" but every subsequent request
   * (e.g. loading the admin dashboard) fails with a confusing 403.
   */
  private routeAfterLogin(mustChangePassword: boolean): void {
    this.router.navigate([mustChangePassword ? "/change-password" : "/"]);
  }
}
