-- Enterprise security hardening (Section 2): MFA. No TOTP field existed on users before
-- this -- confirmed via full-repo grep in the master prompt's ground-truth pass. This is
-- a real subsystem, not a toggle.

ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
-- Encrypted at rest by TotpEncryptionService (AES-256-GCM, key from MFA_ENCRYPTION_KEY)
-- before ever reaching this column -- never store a plaintext TOTP secret.
ALTER TABLE users ADD COLUMN totp_secret VARCHAR(500);
ALTER TABLE users ADD COLUMN mfa_enrolled_at TIMESTAMP;

-- One-time recovery codes for an MFA-enrolled account, hashed with the same
-- PasswordEncoder (BCrypt) used for login passwords. Each code is single-use: consuming
-- one deletes it rather than flipping a "used" flag, so the live set is always exactly
-- "codes still available" with no need to filter used ones out on every read.
CREATE TABLE mfa_recovery_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_mfa_recovery_codes_user ON mfa_recovery_codes (user_id);
