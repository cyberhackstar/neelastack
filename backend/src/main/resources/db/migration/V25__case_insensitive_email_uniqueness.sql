-- P0 fix: application code now normalizes every email to trim+lowercase before it ever
-- reaches the database (AuthService.normalizeEmail / UserDetailsServiceImpl), but the
-- schema itself did not previously guarantee this, so a direct insert, an admin tool, or
-- a future code path could still create "user@example.com" and "User@Example.com" as two
-- logically-identical-but-distinct accounts. Enforce it at the data layer as the final gate.

-- 1) Normalize any pre-existing rows so the new index can be created without a conflict.
UPDATE users SET email = lower(trim(email)) WHERE email <> lower(trim(email));

-- 2) Case-insensitive uniqueness, independent of the plain UNIQUE constraint on the column.
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_lower_unique ON users (lower(email));
