package com.neelastack.dto.engagement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProjectMessageRequest(
        @NotBlank @Size(max = 5000) String body,
        // Optional: id of a ProjectFile already uploaded via the existing
        // /engagements/{id}/files endpoint. Must belong to the same engagement --
        // ProjectMessageService verifies this rather than trusting the client.
        UUID attachmentFileId
) {}
