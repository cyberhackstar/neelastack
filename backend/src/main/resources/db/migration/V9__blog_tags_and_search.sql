-- Neelastack platform - blog tags for search/filtering/related posts

CREATE TABLE blog_post_tags (
    blog_post_id  UUID NOT NULL REFERENCES blog_posts(id) ON DELETE CASCADE,
    tag           VARCHAR(60) NOT NULL
);

CREATE INDEX idx_blog_post_tags_tag ON blog_post_tags (tag);

-- Full-text-ish search support: index for ILIKE search on title/excerpt.
CREATE INDEX idx_blog_posts_title ON blog_posts (title);
