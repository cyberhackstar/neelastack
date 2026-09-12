-- Client Workspace: in-project messaging between the client and Neelastack staff.
--
-- project_messages: append-only transcript per engagement. attachment_file_id optionally
-- points at a row already uploaded through the existing project_files flow (Section 8 of
-- the review: attachments should reuse the existing document pipeline rather than a second
-- one). ON DELETE SET NULL so removing the underlying file (see ProjectFileService#delete)
-- never blocks deleting a message or vice versa -- the message body stands on its own even
-- if the attached file is later removed.
CREATE TABLE project_messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    engagement_id       UUID NOT NULL REFERENCES engagements(id) ON DELETE CASCADE,
    sender_id           UUID NOT NULL REFERENCES users(id),
    body                TEXT NOT NULL,
    attachment_file_id  UUID REFERENCES project_files(id) ON DELETE SET NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_project_messages_engagement ON project_messages (engagement_id, created_at);

-- project_message_reads: one row per (engagement, user) tracking the last time that user
-- caught up on the thread, so unread counts can be computed as "messages after my
-- last_read_at that I didn't send myself" without storing a per-message read flag per user.
CREATE TABLE project_message_reads (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    engagement_id   UUID NOT NULL REFERENCES engagements(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    last_read_at    TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (engagement_id, user_id)
);

CREATE INDEX idx_project_message_reads_lookup ON project_message_reads (engagement_id, user_id);
