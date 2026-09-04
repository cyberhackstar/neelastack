-- Neelastack platform - richer, premium service descriptions

UPDATE services SET description =
'A complete web application, built and shipped end-to-end: Spring Boot REST API on the backend, Angular on the front, PostgreSQL for data, deployed with Docker and CI/CD from day one. Includes authentication, role-based access, and a production-ready deployment — not a demo that needs another six weeks of work before it can go live. Fixed scope, fixed price, agreed before any code is written.'
WHERE slug = 'full-stack-web-application';

UPDATE services SET description =
'A REST API designed to be someone else''s dependency for years, not months: JWT authentication, input validation, structured error handling, OpenAPI documentation, and a schema that migrates cleanly as requirements change. Built for integration — whether that''s your own frontend, a mobile app, or a third-party partner consuming your endpoints.'
WHERE slug = 'api-backend-engineering';

UPDATE services SET description =
'Your application, containerized and deployed with a real CI/CD pipeline — GitHub Actions building, testing, and shipping Docker images on every push, Nginx handling SSL and reverse proxying, and monitoring in place so you find out about problems before your users do. If you already have infrastructure (AWS, GCP, Oracle Cloud, a VPS), I''ll work with what you have rather than starting over.'
WHERE slug = 'cloud-deployment-devops';

UPDATE services SET description =
'A structured review of an existing codebase — security vulnerabilities, N+1 queries and slow endpoints, unhandled edge cases, and architectural decisions that are quietly becoming technical debt. You get a prioritized, written report: what''s urgent, what can wait, and what it would take to fix each item. No obligation to hire me for the fixes.'
WHERE slug = 'code-audit-performance';
