-- Neelastack platform - initial schema
-- Requires PostgreSQL 15+

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name       VARCHAR(120) NOT NULL,
    email           VARCHAR(180) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    phone           VARCHAR(20),
    role            VARCHAR(20) NOT NULL DEFAULT 'CLIENT',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role ON users (role);

-- Seed an initial admin account.
-- Password below is a bcrypt hash for "ChangeMe@123" — CHANGE THIS IMMEDIATELY after first deploy.
INSERT INTO users (full_name, email, password, role, enabled, email_verified)
VALUES (
    'Neelastack Admin',
    'admin@neelastack.com',
    '$2b$12$jjJgov3yBbCvo6q0UWnunejnGqFHTCV3dy7ZYjJnYN66fp32lYhB6',
    'ADMIN',
    TRUE,
    TRUE
);
