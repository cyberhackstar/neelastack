package com.neelastack.dto.engagement;

/** {@code assigneeEmail} is nullable by design: passing null unassigns the task. */
public record ProjectTaskAssignRequest(
        String assigneeEmail
) {}
