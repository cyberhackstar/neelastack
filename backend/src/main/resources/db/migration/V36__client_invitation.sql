-- Client Workspace: let an admin create an engagement for a client who does not yet have
-- an account, instead of requiring the client to self-register first (Section 5 of the
-- review: "Don't require the client to already have an account").
--
-- invitation_pending marks a User row created as a placeholder for an invited client: it
-- has an unusable random-hash password and enabled=false (see User#isEnabled /
-- EngagementService#inviteClient) until the client sets a real password via the emailed
-- invitation link (AuthService#acceptInvitation) or signs in with Google using the same
-- address (OAuth2LoginSuccessHandler activates the invite on that path too).
ALTER TABLE users ADD COLUMN invitation_pending BOOLEAN NOT NULL DEFAULT FALSE;
