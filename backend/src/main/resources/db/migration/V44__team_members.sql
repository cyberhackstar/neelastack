-- Public team-member CMS managed by SUPERADMIN.
CREATE TABLE team_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    role VARCHAR(120) NOT NULL,
    bio TEXT NOT NULL,
    skills TEXT NOT NULL DEFAULT '',
    photo_url VARCHAR(1000),
    photo_public_id VARCHAR(500),
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_team_members_active_order ON team_members (active, sort_order, name);

INSERT INTO team_members (name, role, bio, skills, photo_url, photo_public_id, sort_order, active) VALUES
('Bhawesh Sharma', 'Founder & Lead Engineer',
 'Runs every engagement end-to-end — architecture, backend, frontend, and deployment. Full-stack Java developer with production experience in enterprise Spring Boot systems.',
 'Spring Boot,Angular,Microservices,PostgreSQL',
 'https://res.cloudinary.com/ddrt7emvo/image/upload/v1789237019/bhawesh_team_xcuzpk.png',
 'bhawesh_team_xcuzpk', 10, TRUE),
('Padmasinha Chitte', 'Collaborating Engineer',
 'Brought in on select engagements that need extra hands or a second set of eyes on architecture decisions.',
 'Software Engineering',
 'https://res.cloudinary.com/ddrt7emvo/image/upload/v1789237020/padam_team_x16tpe.png',
 'padam_team_x16tpe', 20, TRUE),
('Anuragdeep Srivastav', 'Collaborating Engineer',
 'Brought in on select engagements that need extra hands or a second set of eyes on architecture decisions.',
 'Software Engineering',
 'https://res.cloudinary.com/ddrt7emvo/image/upload/v1789237020/anurag_team_lunwb2.png',
 'anurag_team_lunwb2', 30, TRUE);
