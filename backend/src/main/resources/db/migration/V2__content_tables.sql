-- Neelastack platform - content tables: services, projects, blog posts

CREATE TABLE services (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(120) NOT NULL,
    slug            VARCHAR(160) NOT NULL UNIQUE,
    summary         VARCHAR(300) NOT NULL,
    description     TEXT,
    icon            VARCHAR(60),
    starting_price  VARCHAR(40),
    display_order   INTEGER NOT NULL DEFAULT 0,
    published       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE projects (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title               VARCHAR(160) NOT NULL,
    slug                VARCHAR(180) NOT NULL UNIQUE,
    summary             VARCHAR(300) NOT NULL,
    problem_statement   TEXT,
    solution            TEXT,
    outcome             TEXT,
    cover_image_url     VARCHAR(300),
    live_url            VARCHAR(300),
    repo_url            VARCHAR(300),
    featured            BOOLEAN NOT NULL DEFAULT FALSE,
    published           BOOLEAN NOT NULL DEFAULT TRUE,
    display_order       INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE project_tech_stack (
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    technology  VARCHAR(60) NOT NULL
);

CREATE TABLE blog_posts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title               VARCHAR(200) NOT NULL,
    slug                VARCHAR(220) NOT NULL UNIQUE,
    excerpt             VARCHAR(320) NOT NULL,
    content             TEXT NOT NULL,
    cover_image_url     VARCHAR(300),
    author_name         VARCHAR(100),
    category            VARCHAR(80),
    meta_title          VARCHAR(160) NOT NULL,
    meta_description    VARCHAR(320) NOT NULL,
    published           BOOLEAN NOT NULL DEFAULT FALSE,
    published_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_projects_published ON projects (published, display_order);
CREATE INDEX idx_blog_posts_published ON blog_posts (published, published_at DESC);

-- Seed: starter services (edit freely via the admin API later)
INSERT INTO services (title, slug, summary, icon, starting_price, display_order) VALUES
('Full-stack Web Application', 'full-stack-web-application', 'End-to-end Spring Boot + Angular builds — from schema design to production deploy.', 'layers', 'Starts at ₹80,000', 1),
('API & Backend Engineering', 'api-backend-engineering', 'Secure, documented REST APIs built for scale — auth, payments, integrations.', 'server', 'Starts at ₹40,000', 2),
('Cloud Deployment & DevOps', 'cloud-deployment-devops', 'Docker, CI/CD, and production hardening on your infrastructure of choice.', 'cloud', 'Starts at ₹25,000', 3),
('Code Audit & Performance', 'code-audit-performance', 'Security review, query optimization, and technical debt cleanup for existing apps.', 'search', 'Starts at ₹20,000', 4);
