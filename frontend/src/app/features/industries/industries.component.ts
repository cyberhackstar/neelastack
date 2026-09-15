import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SeoService } from '../../core/services/seo.service';

interface IndustryCard { slug: string; name: string; short: string; outcome: string; services: string[]; }

@Component({ selector: 'app-industries', standalone: true, imports: [RouterLink], templateUrl: './industries.component.html', styleUrl: './industries.component.scss' })
export class IndustriesComponent implements OnInit {
  private seo = inject(SeoService);
  readonly industries: IndustryCard[] = [
    { slug: 'gyms-fitness', name: 'Gyms & Fitness', short: 'Turn your gym into a digital membership experience.', outcome: 'Attract more members, make bookings easier and reduce day-to-day admin.', services: ['Premium website', 'Membership experience', 'Class & session booking', 'Online payments', 'Admin dashboard'] },
    { slug: 'retail-ecommerce', name: 'Retail & E-commerce', short: 'Take your store beyond your physical location.', outcome: 'Showcase products, sell online and give customers a smoother buying journey.', services: ['Online store', 'Product catalogue', 'Payments & orders', 'Customer accounts', 'Campaign landing pages'] },
    { slug: 'restaurants-hospitality', name: 'Restaurants & Hospitality', short: 'Make it easier for customers to discover, book and order.', outcome: 'Turn local attention into reservations, orders and repeat customers.', services: ['Digital menu', 'Reservation flow', 'Online ordering', 'Offers & campaigns', 'Local SEO foundation'] },
    { slug: 'healthcare-clinics', name: 'Healthcare & Clinics', short: 'Create a trusted digital front door for your practice.', outcome: 'Help patients find you, understand your services and book with confidence.', services: ['Professional website', 'Doctor/service pages', 'Appointment booking', 'Patient communication', 'Search visibility'] },
    { slug: 'professional-services', name: 'Professional Services', short: 'Turn expertise into a stronger digital business.', outcome: 'Build credibility, capture enquiries and automate client workflows.', services: ['Lead-generation website', 'Service pages', 'Lead forms', 'Client portal', 'Workflow automation'] },
    { slug: 'startups', name: 'Startups & New Ventures', short: 'Move from idea to a credible product quickly.', outcome: 'Validate, launch and scale without building throwaway technology.', services: ['MVP product', 'Customer portal', 'Payments', 'Analytics-ready architecture', 'Launch infrastructure'] },
  ];
  ngOnInit(): void {
    this.seo.update({ title: 'Web & Software Solutions for Businesses', description: 'Neelastack helps gyms, retailers, restaurants, clinics, professional services firms and startups build premium websites, web applications and digital systems that attract customers and simplify operations.', path: '/industries' });
    this.seo.setJsonLd([
      { '@context': 'https://schema.org', '@type': 'CollectionPage', name: 'Web & Software Solutions for Businesses', description: 'Business-focused digital solutions from Neelastack.', url: 'https://neelastack.com/industries' },
      { '@context': 'https://schema.org', '@type': 'ItemList', itemListElement: this.industries.map((industry, index) => ({ '@type': 'ListItem', position: index + 1, name: industry.name, url: `https://neelastack.com/industries/${industry.slug}` })) },
    ]);
  }
}
