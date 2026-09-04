-- Programmatic SEO silo: high-intent landing pages targeting specific tech-stack +
-- engagement-type combinations (e.g. "Spring Boot & Angular Enterprise Development
-- Consulting"), separate from the general /services page so each one can rank for its
-- own long-tail query with content that's actually specific to it — not a thin
-- templated stub. Admin-authored, same publish/draft/IndexNow discipline as
-- services/projects/blog (see ServiceContentService, BlogPostService).
CREATE TABLE tech_stack_pages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(160) NOT NULL UNIQUE,
    h1_title VARCHAR(160) NOT NULL,
    meta_title VARCHAR(160) NOT NULL,
    meta_description VARCHAR(300) NOT NULL,
    -- Short hero/dek copy, distinct from the long-form body below, so the template can
    -- render a page that doesn't just repeat /services with a different H1.
    intro TEXT NOT NULL,
    body_content TEXT NOT NULL,
    primary_stack VARCHAR(200) NOT NULL,
    secondary_stack VARCHAR(200),
    target_industry VARCHAR(120),
    -- Concrete use cases actually rendered on the page — mirrors the FAQPage rule
    -- (SchemaBuilderService): structured data must only describe content genuinely
    -- present, so this is stored as real bullet content, not schema-only metadata.
    use_cases TEXT,
    starting_price VARCHAR(40),
    display_order INT NOT NULL DEFAULT 0,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_tech_stack_pages_published ON tech_stack_pages (published, display_order);

-- Seeds the exact example combo named in the original brief, published, so the silo
-- has one real page live immediately instead of shipping as an empty, unusable engine.
-- Everything else (more combos, industries) is a content decision for the admin CMS.
INSERT INTO tech_stack_pages (
    slug, h1_title, meta_title, meta_description, intro, body_content,
    primary_stack, secondary_stack, target_industry, use_cases, starting_price,
    display_order, published
) VALUES (
    'spring-boot-angular-enterprise-development-consulting',
    'Spring Boot & Angular Enterprise Development Consulting',
    'Spring Boot & Angular Enterprise Development Consulting | Neelastack',
    'Fixed-scope Spring Boot 3 and Angular SSR development for enterprise teams — API '
        || 'architecture, modernization, and production-grade delivery with full source ownership.',
    'Enterprise teams building or modernizing on Spring Boot and Angular get a partner who ships '
        || 'production-grade, SSR-ready applications with clear scope and a fixed price.',
    'Spring Boot 3 (Java 21) paired with Angular in SSR mode is a proven combination for '
        || 'enterprise applications that need both a robust, typed backend and fast, '
        || 'SEO-capable server-rendered pages. Typical engagements cover REST/GraphQL API '
        || 'design, JWT/OAuth2 authentication, PostgreSQL schema design with Flyway migrations, '
        || 'and Angular SSR frontends built as standalone components with hydration handled '
        || 'correctly from day one. Every engagement starts with a written, itemized scope — '
        || 'no open-ended hourly billing.',
    'Spring Boot 3, Java 21',
    'Angular (SSR, standalone components)',
    'Enterprise / B2B SaaS',
    'Modernizing a legacy monolith into a Spring Boot API|Building a new customer-facing '
        || 'portal with Angular SSR|Consolidating microservices behind a single Spring Boot '
        || 'gateway|Adding JWT/OAuth2 auth to an existing Spring Boot API',
    'Custom — scoped per engagement',
    0,
    TRUE
);
