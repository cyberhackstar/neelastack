package com.neelastack.dto.engagement;

import com.neelastack.entity.ChangeRequestPriority;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ChangeRequestCreateRequest(
        @NotBlank String title,
        @NotBlank String description,
        ChangeRequestPriority priority,
        // Optional: id of a ProjectFile already uploaded via the existing
        // /engagements/{id}/files endpoint. Must belong to the same engagement --
        // ChangeRequestService verifies this rather than trusting the client, same as
        // ProjectMessageRequest#attachmentFileId.
        UUID attachmentFileId
) {}
