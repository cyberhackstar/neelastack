-- Structured-data foundations (SEO pillar): FAQPage schema needs real Q&A content per
-- service, and Review/AggregateRating schema needs real testimonials per case study.
-- Both are populated by admins through the CMS — never auto-generated — so nothing here
-- risks Google's structured-data spam policy (fabricated or unverifiable ratings).

CREATE TABLE service_faqs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    question VARCHAR(300) NOT NULL,
    answer TEXT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_service_faqs_service_id ON service_faqs (service_id, display_order);

CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    author_name VARCHAR(120) NOT NULL,
    author_title VARCHAR(160),
    -- Whole-star ratings only (schema.org Review.reviewRating.ratingValue accepts
    -- fractional values too, but whole stars are what a client can honestly attest to
    -- without a formal survey instrument behind it).
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_body TEXT NOT NULL,
    -- Reviews are collected before publish is flipped on, so a draft testimonial
    -- never contributes to the public aggregate rating.
    published BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_reviews_project_id ON reviews (project_id, display_order);
CREATE INDEX idx_reviews_published ON reviews (project_id, published);
