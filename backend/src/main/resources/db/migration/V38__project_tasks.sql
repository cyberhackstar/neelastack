-- Client Workspace Section 12: milestones alone aren't enough to actually manage delivery --
-- each milestone breaks down into concrete tasks with their own status, owner, and due date.
CREATE TABLE project_task (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    milestone_id            UUID NOT NULL REFERENCES milestones(id) ON DELETE CASCADE,
    title                   VARCHAR(200) NOT NULL,
    description             TEXT,
    status                  VARCHAR(20) NOT NULL DEFAULT 'TODO',
    due_date                DATE,
    -- Flags tasks like "Review payment flow" that need the client to act, distinct from
    -- internal engineering tasks the client never sees called out separately (Section 12/17).
    client_action_required  BOOLEAN NOT NULL DEFAULT false,
    assignee_id             UUID REFERENCES users(id) ON DELETE SET NULL,
    display_order           INTEGER NOT NULL DEFAULT 0,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_project_task_milestone ON project_task (milestone_id, display_order);
