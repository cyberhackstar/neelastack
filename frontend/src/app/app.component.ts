import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterOutlet } from '@angular/router';
import { NavbarComponent } from './shared/components/navbar/navbar.component';
import { FooterComponent } from './shared/components/footer/footer.component';
import { VerifyBannerComponent } from './shared/components/verify-banner/verify-banner.component';
import { StepUpModalComponent } from './shared/components/step-up-modal/step-up-modal.component';
import { GaAnalyticsService } from './core/services/ga-analytics.service';
import { AttributionService } from './core/services/attribution.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, FooterComponent, VerifyBannerComponent, StepUpModalComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent implements OnInit {
  private ga = inject(GaAnalyticsService);
  private attribution = inject(AttributionService);
  private route = inject(ActivatedRoute);

  ngOnInit(): void {
    this.ga.init();
    this.attribution.captureFirstTouch(this.route);
  }
}
