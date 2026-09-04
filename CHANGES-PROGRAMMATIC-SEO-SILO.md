# Programmatic SEO silo — added on top of the executive-report/ISR-caching pass

## What this is
A real, admin-authored landing-page engine for tech-stack + engagement-type combinations
(e.g. "Spring Boot & Angular Enterprise Development Consulting"), separate from the
general `/services` page so each one can target its own long-tail query with content
actually specific to it. Deliberately **not** a combinatorial auto-generator — mass-
producing thin near-duplicate pages from a slug template is a doorway-page pattern
search engines penalize, which would work against the ranking goal this exists for.
The "programmatic" part is the shared routing/template/schema machinery; the copy on
each page is real and admin-written.

## Backend
- `V17__tech_stack_landing_pages.sql` — new `tech_stack_pages` table, seeded with the
  exact example combo from the brief (published, live immediately).
- `TechStackPage` entity, `TechStackPageRepository`, `TechStackPageDto`/`TechStackPageRequest`.
- `TechStackPageService` — same publish/draft/cache/IndexNow-ping discipline as
  `ServiceContentService`/`BlogPostService`.
- `GET /api/v1/public/solutions`, `GET /api/v1/public/solutions/{slug}` (public reads).
- Full CRUD under `/api/v1/admin/solutions` (added to the existing `AdminContentController`).
- `SeoController` sitemap now includes every published solution page.

## Frontend
- `TechStackPage`/`TechStackPagePayload` models + `ContentService` methods.
- New `/solutions` (list) and `/solutions/:slug` (detail) routes and components, styled
  with the existing card/tag/button classes — no new design system introduced.
- `SchemaBuilderService.buildTechStackSolutionSchema()` — `Service` JSON-LD built only
  from real page fields (name, description, serviceType, audience), same "never fabricate"
  rule as every other schema builder in this file.
- Footer now links to `/solutions` so the silo has an internal-linking path from every
  page — a silo with no internal links pointing to it is much harder to get crawled.

## What you still need to do
No admin UI screen was added for managing these pages yet (the CMS pattern for
services/projects/blog already has one you can extend the same way) — for now, use the
`/api/v1/admin/solutions` endpoints directly (Swagger UI, or your existing admin token).
Everything else — more tech-stack combos, industries, use cases — is content you write
through those endpoints; the engine is ready for it.
