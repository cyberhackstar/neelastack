import { Component, OnInit, HostListener, inject, signal, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ArchitectureDiagramComponent } from '../../shared/components/architecture-diagram/architecture-diagram.component';
import { BrowserMockupComponent } from '../../shared/components/browser-mockup/browser-mockup.component';
import { ContentService } from '../../core/services/content.service';
import { SeoService } from '../../core/services/seo.service';
import { Project, ServiceItem } from '../../core/models/content.model';
import { TiltDirective } from '../../shared/directives/tilt.directive';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, ArchitectureDiagramComponent, BrowserMockupComponent, TiltDirective],
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

  readonly businessPaths = [
    { slug: 'gyms-fitness', title: 'I run a gym or fitness business', copy: 'Memberships, class booking, payments, member experience and a stronger online presence.' },
    { slug: 'retail-ecommerce', title: 'I sell products', copy: 'A premium storefront, ecommerce, payments, orders and a digital customer journey.' },
    { slug: 'restaurants-hospitality', title: 'I run a restaurant or hospitality business', copy: 'Menus, reservations, ordering and a digital experience that turns discovery into action.' },
    { slug: 'healthcare-clinics', title: 'I run a clinic or healthcare practice', copy: 'Trusted service pages, appointment journeys and a professional patient-facing presence.' },
    { slug: 'professional-services', title: 'I sell expertise or services', copy: 'Lead generation, consultation booking, client portals and simpler business workflows.' },
    { slug: 'startups', title: 'I have an idea I want to launch', copy: 'A product, MVP or platform that looks credible now and can keep growing later.' },
  ];

  readonly outcomes = [
    { index: '01', title: 'More customers can find you.', copy: 'A strong online presence gives people a clear path from search and social discovery to enquiry, booking or purchase.' },
    { index: '02', title: 'Customers can do more without calling you.', copy: 'Bookings, purchases, enquiries, accounts and updates can move online — available whenever your business is.' },
    { index: '03', title: 'Your team spends less time on repetitive work.', copy: 'Connect the workflows behind the scenes so information moves between customers, staff, payments and operations.' },
    { index: '04', title: 'Your business looks as good online as it does in person.', copy: 'Premium design and a thoughtful user journey turn your website from an online placeholder into a real business asset.' },
  ];

  readonly steps = [
    { number: '01', title: 'Discover', copy: 'We understand your business, customers, goals and the friction you want to remove.' },
    { number: '02', title: 'Recommend', copy: 'We turn that into a clear solution, scope, timeline and investment.' },
    { number: '03', title: 'Build', copy: 'Design and engineering move together, with regular checkpoints and visible progress.' },
    { number: '04', title: 'Launch & grow', copy: 'We take care of production readiness and leave you with a product designed for the next stage.' },
  ];

  ngOnInit(): void {
    this.seo.update({
      title: 'Web & Software Development for Growing Businesses',
      description:
        'Neelastack helps businesses build premium websites, custom web applications and digital platforms that attract customers, simplify operations and grow online.',
      path: '/',
    });

    this.seo.setJsonLd([
      {
        '@context': 'https://schema.org',
        '@type': 'Organization',
        name: 'Neelastack',
        description: 'Business-first web development and custom software engineering for companies building a stronger digital presence.',
        url: 'https://neelastack.com',
        areaServed: 'IN',
        knowsAbout: ['Web development', 'Custom web applications', 'E-commerce', 'Booking systems', 'Business software', 'Digital transformation', 'Spring Boot', 'Angular'],
        hasOfferCatalog: {
          '@type': 'OfferCatalog',
          name: 'Business digital solutions',
          itemListElement: this.businessPaths.map((item) => ({
            '@type': 'Offer',
            itemOffered: { '@type': 'Service', name: item.title.replace('I ', '') },
          })),
        },
      },
      {
        '@context': 'https://schema.org',
        '@type': 'WebSite',
        name: 'Neelastack',
        url: 'https://neelastack.com',
        description: 'Premium websites, custom web applications and digital platforms for growing businesses.',
      },
    ]);

    this.contentService.getServices().subscribe((data) => this.services.set(data));
    this.contentService.getProjects(true).subscribe((data) => this.featuredProjects.set(data));
  }
}
