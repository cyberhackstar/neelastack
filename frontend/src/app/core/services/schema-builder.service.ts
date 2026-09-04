import { Injectable } from '@angular/core';
import { BlogPost, Project, ServiceItem } from '../models/content.model';

const SITE_URL = 'https://neelastack.com';
const ORG_NAME = 'Neelastack';

/**
 * Builds schema.org JSON-LD objects from data that already exists in the app —
 * never from placeholders or estimates. Every method here either returns a schema
 * backed by real content, or returns `null` so the caller emits nothing rather than
 * a hollow/misleading structured-data block. This matters beyond style: Google's
 * structured-data guidelines treat fabricated ratings, fake reviews, or content not
 * actually present on the page as spam, with a manual-action risk attached.
 */
@Injectable({ providedIn: 'root' })
export class SchemaBuilderService {
  /**
   * TechArticle is the more specific, higher-signal type for developer/engineering
   * content; generic Article is correct for posts that aren't really technical
   * (case studies, business/process posts). Deciding by category keeps the schema
   * honest rather than defaulting every post to the fancier type.
   */
  private static readonly TECH_CATEGORIES = new Set([
    'engineering',
    'backend',
    'frontend',
    'devops',
    'architecture',
    'security',
    'performance',
    'database',
    'api',
    'cloud',
  ]);

  buildArticleSchema(post: BlogPost) {
    const isTechnical = post.category
      ? SchemaBuilderService.TECH_CATEGORIES.has(post.category.toLowerCase())
      : false;

    return {
      '@context': 'https://schema.org',
      '@type': isTechnical ? 'TechArticle' : 'Article',
      headline: post.title,
      description: post.excerpt,
      image: post.coverImageUrl ? [post.coverImageUrl] : undefined,
      datePublished: post.publishedAt,
      dateModified: post.publishedAt,
      author: { '@type': 'Person', name: post.authorName ?? ORG_NAME },
      publisher: {
        '@type': 'Organization',
        name: ORG_NAME,
        logo: { '@type': 'ImageObject', url: `${SITE_URL}/assets/og/og-image.png` },
      },
      mainEntityOfPage: { '@type': 'WebPage', '@id': `${SITE_URL}/blog/${post.slug}` },
      keywords: post.tags?.length ? post.tags.join(', ') : undefined,
    };
  }

  buildBreadcrumbSchema(items: Array<{ name: string; path: string }>) {
    return {
      '@context': 'https://schema.org',
      '@type': 'BreadcrumbList',
      itemListElement: items.map((item, index) => ({
        '@type': 'ListItem',
        position: index + 1,
        name: item.name,
        item: `${SITE_URL}${item.path}`,
      })),
    };
  }

  /**
   * FAQPage from real, admin-authored Q&A only. Pass in every FAQ actually rendered
   * on the page (page-level + any per-service FAQs) — never a superset the visitor
   * can't also see, which is a Google structured-data guideline requirement, not
   * just a nicety.
   */
  buildFaqSchema(faqs: Array<{ question: string; answer: string }>) {
    if (!faqs.length) return null;
    return {
      '@context': 'https://schema.org',
      '@type': 'FAQPage',
      mainEntity: faqs.map((faq) => ({
        '@type': 'Question',
        name: faq.question,
        acceptedAnswer: { '@type': 'Answer', text: faq.answer },
      })),
    };
  }

  /**
   * Review + AggregateRating for a portfolio case study. Returns null when there are
   * no published reviews — an empty/placeholder rating is worse than none.
   */
  buildCaseStudyReviewSchema(project: Project & { reviews?: any[]; averageRating?: number | null; reviewCount?: number }) {
    const reviews = project.reviews ?? [];
    if (!reviews.length || !project.averageRating || !project.reviewCount) return null;

    return {
      '@context': 'https://schema.org',
      '@type': 'CreativeWork',
      name: project.title,
      about: project.summary,
      aggregateRating: {
        '@type': 'AggregateRating',
        ratingValue: project.averageRating,
        reviewCount: project.reviewCount,
        bestRating: 5,
        worstRating: 1,
      },
      review: reviews.map((r: any) => ({
        '@type': 'Review',
        author: { '@type': 'Person', name: r.authorName },
        reviewRating: { '@type': 'Rating', ratingValue: r.rating, bestRating: 5, worstRating: 1 },
        reviewBody: r.reviewBody,
      })),
    };
  }

  /**
   * SoftwareApplication describing the CLIENT's product built in a case study — not
   * Neelastack itself, which is a services business, not a software product. Only
   * emitted when the project has a live, working deployment to point to; otherwise
   * `applicationCategory`/`operatingSystem` claims would be unverifiable.
   */
  buildCaseStudySoftwareSchema(project: Project) {
    if (!project.liveUrl) return null;
    return {
      '@context': 'https://schema.org',
      '@type': 'SoftwareApplication',
      name: project.title,
      description: project.summary,
      url: project.liveUrl,
      applicationCategory: 'BusinessApplication',
      operatingSystem: 'Web',
    };
  }

  /** Page-wide FAQPage on /services, combining the static guarantee FAQs with any
   *  per-service FAQs an admin has added — all real, all visible on the page. */
  buildServicesFaqSchema(
    staticFaqs: Array<{ question: string; answer: string }>,
    services: ServiceItem[],
  ) {
    const serviceFaqs = services.flatMap((s) => (s as any).faqs ?? []);
    return this.buildFaqSchema([...staticFaqs, ...serviceFaqs]);
  }

  /**
   * Service schema for a programmatic-SEO solution page (e.g. "Spring Boot & Angular
   * Enterprise Development Consulting"). serviceType and description are built directly
   * from the admin-authored page content — never invented — matching the same
   * "only schema what's actually on the page" rule as everything else here.
   */
  buildTechStackSolutionSchema(page: {
    h1Title: string;
    metaDescription: string;
    slug: string;
    primaryStack: string;
    secondaryStack?: string;
    targetIndustry?: string;
  }) {
    return {
      '@context': 'https://schema.org',
      '@type': 'Service',
      name: page.h1Title,
      description: page.metaDescription,
      serviceType: [page.primaryStack, page.secondaryStack].filter(Boolean).join(' + '),
      areaServed: 'Worldwide',
      audience: page.targetIndustry
        ? { '@type': 'Audience', audienceType: page.targetIndustry }
        : undefined,
      provider: {
        '@type': 'ProfessionalService',
        name: ORG_NAME,
        url: SITE_URL,
      },
      url: `${SITE_URL}/solutions/${page.slug}`,
    };
  }
}
