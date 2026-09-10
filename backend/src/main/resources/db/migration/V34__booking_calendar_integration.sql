-- Optional Google Calendar sync for the booking engine (V33). Free to use (Google
-- Calendar API's free tier), off by default (app.google-calendar.enabled=false) --
-- see GoogleCalendarService. Storing exactly one row is the expected steady state
-- (single-calendar business), but nothing here prevents adding more later.

CREATE TABLE calendar_connections (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider          VARCHAR(20) NOT NULL DEFAULT 'GOOGLE',
    connected_email   VARCHAR(180),
    calendar_id       VARCHAR(200) NOT NULL DEFAULT 'primary',
    access_token      TEXT NOT NULL,
    refresh_token     TEXT NOT NULL,
    token_expires_at  TIMESTAMP NOT NULL,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    last_synced_at    TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE calendar_connections IS
    'OAuth tokens for the admin''s connected calendar (feature 3/4: read busy blocks,
     write created-booking events with a Google Meet link). access_token/refresh_token
     are plaintext at the DB layer today, same trust boundary as this app''s other
     third-party secrets (env vars) -- rotate immediately if the DB is ever exposed.
     Encrypt-at-rest via a KMS/Vault-backed column is a documented follow-up, not done
     here, to avoid adding a new crypto dependency to this change.';
