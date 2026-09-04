import { Component, OnInit, HostListener, inject, signal, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ArchitectureDiagramComponent } from '../../shared/components/architecture-diagram/architecture-diagram.component';
import { BrowserMockupComponent } from '../../shared/components/browser-mockup/browser-mockup.component';
import { ContentService } from '../../core/services/content.service';
import { SeoService } from '../../core/services/seo.service';
import { Project, ServiceItem } from '../../core/models/content.model';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, ArchitectureDiagramComponent, BrowserMockupComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  private contentService = inject(ContentService);
  private seo = inject(SeoService);
  private platformId = inject(PLATFORM_ID);

  services = signal<ServiceItem[]>([]);
  featuredProjects = signal<Project[]>([]);

  /** Tiny hero-visual parallax offset (desktop pointer only). Stays at 0,0 on
   *  touch devices and when the user prefers reduced motion — see the
   *  isParallaxEnabled() guard below. */
  parallax = signal({ x: 0, y: 0 });

  private isParallaxEnabled(): boolean {
    if (!isPlatformBrowser(this.platformId)) return false;
    const finePointer = window.matchMedia('(pointer: fine)').matches;
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    return finePointer && !reducedMotion;
  }

  @HostListener('window:mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    if (!this.isParallaxEnabled()) return;
    const xRatio = event.clientX / window.innerWidth - 0.5;
    const yRatio = event.clientY / window.innerHeight - 0.5;
    // Deliberately tiny — a few pixels of drift, not a moving page.
    this.parallax.set({ x: -xRatio * 8, y: -yRatio * 6 });
  }

  readonly stats = [
    { value: '< 200ms', label: 'p95 API response' },
    { value: '99.9%', label: 'uptime target' },
    { value: 'A+', label: 'SSL Labs rating' },
  ];

  readonly trustItems = [
    'JWT Authentication',
    'Role-based Access Control',
    'Rate Limiting',
    'Encrypted Transport',
    'Automated Testing',
    'CI/CD Deployment',
  ];

  ngOnInit(): void {
    this.seo.update({
      title: 'Enterprise-grade Web Applications',
      description:
        'Neelastack is an independent software engineering practice building fast, secure, production-grade web applications with Spring Boot and Angular.',
      path: '/',
    });

    this.seo.setJsonLd({
      '@context': 'https://schema.org',
      '@type': 'ProfessionalService',
      name: 'Neelastack',
      description: 'Independent full-stack engineering practice — Spring Boot and Angular specialists.',
      url: 'https://neelastack.com',
      // TODO: fill in once available — these materially help local/service
      // search relevance and rich-result eligibility, but must stay accurate.
      // areaServed: 'IN',
      // sameAs: ['https://github.com/<handle>', 'https://linkedin.com/in/<handle>'],
      // telephone / priceRange: only add once decided and true.
      knowsAbout: ['Spring Boot', 'Angular', 'PostgreSQL', 'System Architecture', 'API Security'],
      makesOffer: [
        { '@type': 'Offer', itemOffered: { '@type': 'Service', name: 'New web application development' } },
        { '@type': 'Offer', itemOffered: { '@type': 'Service', name: 'Application fixes & performance audits' } },
        { '@type': 'Offer', itemOffered: { '@type': 'Service', name: 'Legacy system modernization' } },
      ],
    });

    this.contentService.getServices().subscribe((data) => this.services.set(data));
    this.contentService.getProjects(true).subscribe((data) => this.featuredProjects.set(data));
  }
}
