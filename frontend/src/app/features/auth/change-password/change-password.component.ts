import { Component, OnInit, inject, signal } from "@angular/core";
import {
  ReactiveFormsModule,
  FormBuilder,
  Validators,
  AbstractControl,
  ValidationErrors,
} from "@angular/forms";
import { Router } from "@angular/router";
import { AuthService } from "../../../core/services/auth.service";
import { SeoService } from "../../../core/services/seo.service";
import { LogoComponent } from "../../../shared/components/logo/logo.component";

function passwordsMatch(control: AbstractControl): ValidationErrors | null {
  const newPassword = control.get("newPassword")?.value;
  const confirmPassword = control.get("confirmPassword")?.value;
  return newPassword && confirmPassword && newPassword !== confirmPassword
    ? { mismatch: true }
    : null;
}

/**
 * Mandatory password-change gate. Reached after login when AuthResponse.mustChangePassword
 * is true — a freshly-bootstrapped admin (see AdminBootstrapRunner) or any account an admin
 * has force-reset. MustChangePasswordFilter rejects every other authenticated API call for
 * that account, so this page is the only place such a user can go until they complete it.
 */
@Component({
  selector: "app-change-password",
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: "./change-password.component.html",
  styleUrl: "./change-password.component.scss",
})
export class ChangePasswordComponent implements OnInit {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private seo = inject(SeoService);

  loading = signal(false);
  errorMessage = signal<string | null>(null);

  form = this.fb.nonNullable.group(
    {
      currentPassword: ["", [Validators.required]],
      newPassword: ["", [Validators.required, Validators.minLength(12)]],
      confirmPassword: ["", [Validators.required]],
    },
    { validators: passwordsMatch },
  );

  ngOnInit(): void {
    this.seo.update({
      title: "Change Password",
      description: "Set a new password for your account.",
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

    const { currentPassword, newPassword } = this.form.getRawValue();

    this.authService.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(["/"]);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(
          err?.error?.message ?? "Unable to change password. Please try again.",
        );
      },
    });
  }
}
