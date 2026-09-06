import { Component, OnInit, inject, signal } from "@angular/core";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { AuthService } from "../../../core/services/auth.service";
import { SeoService } from "../../../core/services/seo.service";
import { GoogleSigninButtonComponent } from "../../../shared/components/google-signin-button/google-signin-button.component";
import { LogoComponent } from "../../../shared/components/logo/logo.component";

@Component({
  selector: "app-register",
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, GoogleSigninButtonComponent],
  templateUrl: "./register.component.html",
  styleUrl: "./register.component.scss",
})
export class RegisterComponent implements OnInit {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private seo = inject(SeoService);

  loading = signal(false);
  errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.seo.update({
      title: "Create Account",
      description: "Create your Neelastack account.",
      noindex: true,
    });
  }

  form = this.fb.nonNullable.group({
    fullName: ["", [Validators.required, Validators.minLength(2)]],
    email: ["", [Validators.required, Validators.email]],
    password: ["", [Validators.required, Validators.minLength(8)]],
    phone: [""],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.register(this.form.getRawValue()).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(["/"]);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(
          err?.error?.message ?? "Unable to create account. Please try again.",
        );
      },
    });
  }
}
