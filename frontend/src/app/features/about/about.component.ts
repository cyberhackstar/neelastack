import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SeoService } from '../../core/services/seo.service';

@Component({
  selector: 'app-about',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './about.component.html',
  styleUrl: './about.component.scss',
})
export class AboutComponent implements OnInit {
  private seo = inject(SeoService);

  readonly skillGroups = [
    { label: 'Languages', items: ['Java', 'JavaScript', 'TypeScript', 'Python', 'C++', 'SQL'] },
    { label: 'Backend', items: ['Spring Boot', 'REST APIs', 'JWT', 'Microservices', 'Kafka', 'ActiveMQ'] },
    { label: 'Frontend', items: ['Angular', 'HTML5', 'CSS3', 'Responsive design'] },
    { label: 'Data', items: ['PostgreSQL', 'MySQL', 'Redis'] },
    { label: 'DevOps', items: ['Docker', 'GitHub Actions', 'Jenkins', 'Nginx', 'Grafana'] },
  ];

  readonly process = [
    {
      title: 'Discovery',
      description: 'A working session to pin down the real problem, the constraints that matter, and what "done" looks like — before any architecture gets decided.',
    },
    {
      title: 'Scoped proposal',
      description: 'A written scope with a fixed price and timeline. You approve it before anything is built — no surprise invoices, no scope creep by accident.',
    },
    {
      title: 'Build in the open',
      description: 'Milestones tracked on your own dashboard, with working software to look at along the way — not a single reveal at the end.',
    },
    {
      title: 'Ship & support',
      description: 'Deployed with CI/CD, monitoring, and documentation, plus a defined support window after launch — not a system that becomes unmaintainable the day I hand it over.',
    },
  ];


  readonly principles = [
    { number: '01', title: 'Clarity before complexity', description: 'We start with the business outcome, then design only the technology needed to get there.' },
    { number: '02', title: 'Production is the product', description: 'Security, testing, observability, deployment and maintainability belong in the definition of done.' },
    { number: '03', title: 'Direct accountability', description: 'Decisions stay close to the people building the system, so context does not get lost between layers.' },
  ];

  readonly signals = [
    { value: 'Business-first', label: 'Every recommendation tied to an outcome' },
    { value: 'Full-stack', label: 'Product, platform and delivery under one roof' },
    { value: 'Production-ready', label: 'Built for real users, real data and real operations' },
  ];

  readonly certifications = [
    'Oracle Certified Java Programmer (OCJP)',
    'Data Structures & Algorithms — UpGrad',
    'Cyber Security Expert — MSME, Government of India',
  ];

  ngOnInit(): void {
    this.seo.update({
      title: 'About',
      description: 'Neelastack helps growing businesses turn ambitious ideas into premium digital products, customer experiences and production-ready software.',
      path: '/about',
    });

    this.seo.setJsonLd({
      '@context': 'https://schema.org',
      '@type': 'Person',
      name: 'Bhawesh Sharma',
      jobTitle: 'Full Stack Java Developer',
      url: 'https://neelastack.com/about',
      worksFor: { '@type': 'Organization', name: 'Neelastack' },
      knowsAbout: ['Java', 'Spring Boot', 'Angular', 'Microservices', 'PostgreSQL', 'Apache Kafka'],
    });
  }
}
