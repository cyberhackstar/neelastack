-- Neelastack platform - real portfolio projects

INSERT INTO projects (title, slug, summary, problem_statement, solution, outcome, live_url, featured, published, display_order)
VALUES
(
    'ElectroMart — Electrical Supplies E-Commerce',
    'electromart-electrical-supplies-ecommerce',
    'A full-catalog e-commerce storefront for electrical supplies — wiring, switchgear, lighting, and tools — built for both retail and bulk contractor buyers.',
    'Electricians and contractors buying supplies online need to trust that what they receive matches what''s rated on the box, and they need bulk-order pricing that retail storefronts don''t usually offer.',
    'Built a category-driven storefront (wiring & cables, switches & sockets, MCBs & breakers, lighting, fans, tools) with cart, wishlist, and account/order tracking, Razorpay-encrypted checkout alongside Cash on Delivery, and a tiered bulk-pricing path for contractors and electricians.',
    'A live, functioning storefront with same-day dispatch messaging, full checkout flow, and a product catalog structured for both individual home buyers and repeat contractor customers.',
    'https://electricalmart.netlify.app',
    true,
    true,
    1
),
(
    'Ladies Apparel — Fashion E-Commerce Storefront',
    'ladies-apparel-fashion-ecommerce',
    'A women''s fashion e-commerce storefront covering browsing, cart, and checkout for an apparel catalog.',
    'A clean, fast-loading storefront for a fashion catalog, built to handle product browsing and checkout without the bloat that typically slows down e-commerce sites.',
    'Built as a modern single-page storefront with a product catalog, cart, and checkout flow, optimized for fast page loads on mobile.',
    'A live storefront handling the full browse-to-checkout journey for an apparel catalog.',
    'https://apparel.bhawesh.shop',
    true,
    true,
    2
),
(
    'GymAI — AI-Powered Fitness Platform',
    'gymai-fitness-platform',
    'A microservices-based fitness platform that generates personalized workout and diet plans, with real-time progress tracking over Kafka.',
    'Generic workout apps don''t adapt to an individual''s progress, and most fitness platforms are built as a single monolith that can''t scale specific pieces — like the recommendation engine — independently.',
    'Designed a microservices architecture with Spring Boot handling core services and JWT-based auth across all of them, a FastAPI service dedicated to AI-driven workout and diet recommendations, and Kafka for event-driven, real-time progress tracking between services. Fully Dockerized and self-hosted on a Raspberry Pi personal cloud server.',
    'A working platform demonstrating production-grade patterns — service-to-service auth, event-driven architecture, and a real ML-backed recommendation service — running end-to-end on self-managed infrastructure.',
    null,
    true,
    true,
    3
),
(
    'Car Rental Platform',
    'car-rental-platform',
    'A microservices-based car rental system handling bookings, payments, and real-time vehicle availability.',
    'Vehicle booking, payment processing, and availability tracking are naturally separate concerns that need to stay consistent with each other under concurrent bookings — a common distributed-systems challenge.',
    'Built separate services for booking, payment, and vehicle availability, connected via ActiveMQ for asynchronous messaging, with Razorpay handling payments and Google OAuth for authentication.',
    'A functioning multi-service booking platform demonstrating message-driven consistency across independently deployable services.',
    null,
    false,
    true,
    4
);

-- Tech stack tags for each project
INSERT INTO project_tech_stack (project_id, technology)
SELECT id, tech FROM projects, UNNEST(ARRAY['Angular', 'Spring Boot', 'PostgreSQL', 'Razorpay']) AS tech
WHERE slug = 'electromart-electrical-supplies-ecommerce';

INSERT INTO project_tech_stack (project_id, technology)
SELECT id, tech FROM projects, UNNEST(ARRAY['Angular', 'E-Commerce', 'Responsive Design']) AS tech
WHERE slug = 'ladies-apparel-fashion-ecommerce';

INSERT INTO project_tech_stack (project_id, technology)
SELECT id, tech FROM projects, UNNEST(ARRAY['Spring Boot', 'Angular', 'FastAPI', 'Kafka', 'PostgreSQL', 'Docker']) AS tech
WHERE slug = 'gymai-fitness-platform';

INSERT INTO project_tech_stack (project_id, technology)
SELECT id, tech FROM projects, UNNEST(ARRAY['Spring Boot', 'Angular', 'PostgreSQL', 'ActiveMQ', 'Razorpay']) AS tech
WHERE slug = 'car-rental-platform';
