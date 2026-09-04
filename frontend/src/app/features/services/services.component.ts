import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ContentService } from '../../core/services/content.service';
import { SeoService } from '../../core/services/seo.service';
import { SchemaBuilderService } from '../../core/services/schema-builder.service';
import { BreadcrumbComponent, BreadcrumbItem } from '../../shared/components/breadcrumb/breadcrumb.component';
import { ServiceItem } from '../../core/models/content.model';

interface Faq {
  question: string;
  answer: string;
}

@Component({
  selector: 'app-services',
  standalone: true,
  imports: [RouterLink, BreadcrumbComponent],
  templateUrl: './services.component.html',
  styleUrl: './services.component.scss',
})
export class ServicesComponent implements OnInit {
  private contentService = inject(ContentService);
  private seo = inject(SeoService);
  private schema = inject(SchemaBuilderService);

  services = signal<ServiceItem[]>([]);
  readonly breadcrumbItems: BreadcrumbItem[] = [{ name: 'Services', path: '/services' }];

  readonly guarantees = [
    {
      title: 'Fixed price, fixed scope',
      description: 'You get a written proposal before any code is written. The price you approve is the price you pay.',
    },
    {
      title: 'You own the code',
      description: 'Full source, full repository access, no vendor lock-in. It\'s your codebase from the first commit.',
    },
    {
      title: 'Built to be handed off',
      description: 'Documentation, clean commit history, and infrastructure-as-code — so any future developer, including one that isn\'t me, can pick it up.',
    },
  ];

  readonly faqs: Faq[] = [
    {
      question: 'How is pricing determined?',
      answer: 'Every engagement gets a written, itemized quotation before work starts — based on scope, not hours logged. You know the total cost upfront and approve it before anything is built.',
    },
    {
      question: 'How long does a typical project take?',
      answer: 'A focused MVP or backend API typically takes 3–6 weeks. Larger full-stack builds with multiple modules run 6–12 weeks. Your proposal will include a specific timeline before you commit.',
    },
    {
      question: 'Do you work with clients outside India?',
      answer: 'Yes. Communication happens over email and your project dashboard, with calls scheduled across time zones as needed. Payments are handled through Razorpay.',
    },
    {
      question: 'What happens after the project launches?',
      answer: 'Every engagement includes a defined post-launch support window for fixes and questions. Ongoing maintenance or feature work beyond that is scoped as a separate, smaller engagement.',
    },
    {
      question: 'Can you take over an existing codebase?',
      answer: 'Yes — a code audit is often the right first step: a structured review of what exists today, what\'s solid, and what needs attention, before committing to further work.',
    },
  ];

  ngOnInit(): void {
    this.seo.update({
      title: 'Services & Pricing',
      description: 'Full-stack web application development, API engineering, cloud deployment, and code audits — scoped, fixed-outcome engagements with Spring Boot and Angular.',
      path: '/services',
    });

    this.contentService.getServices().subscribe((data) => {
      this.services.set(data);

      // FAQPage combines the static guarantee-FAQs above with any per-service FAQs an
      // admin has added in the CMS — all of it real content actually rendered on this
      // page, which is what Google's FAQPage guidelines require.
      const schemas = [
        this.schema.buildBreadcrumbSchema(this.breadcrumbItems),
        this.schema.buildServicesFaqSchema(this.faqs, data),
      ].filter((s): s is NonNullable<typeof s> => s !== null);

      this.seo.setJsonLd(schemas);
    });
  }
}
