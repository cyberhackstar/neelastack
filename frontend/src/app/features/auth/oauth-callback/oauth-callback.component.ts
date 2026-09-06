import { Component, OnInit, inject, signal } from "@angular/core";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { AuthService } from "../../../core/services/auth.service";
import { SeoService } from "../../../core/services/seo.service";
import { LogoComponent } from "../../../shared/components/logo/logo.component";

@Component({
  selector: "app-oauth-callback",
  standalone: true,
  imports: [RouterLink],
  templateUrl: "./oauth-callback.component.html",
  styleUrl: "./oauth-callback.component.scss",
})
export class OAuthCallbackComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(AuthService);
  private seo = inject(SeoService);

  status = signal<"exchanging" | "error">("exchanging");

  ngOnInit(): void {
    this.seo.update({
      title: "Signing In",
      description: "Completing sign-in to your Neelastack account.",
      noindex: true,
    });

    const code = this.route.snapshot.queryParamMap.get("code");
    if (!code) {
      this.status.set("error");
      return;
    }

    this.authService.exchangeOAuthCode(code).subscribe({
      next: () => this.router.navigate(["/dashboard"]),
      error: () => this.status.set("error"),
    });
  }
}
