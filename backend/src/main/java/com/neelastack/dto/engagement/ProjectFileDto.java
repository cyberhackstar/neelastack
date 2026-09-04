package com.neelastack.dto.engagement;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ProjectFileDto(
        UUID id,
        String fileName,
        String fileUrl,
        String fileType,
        Long fileSizeBytes,
        String uploadedByName,
        LocalDateTime createdAt
) {}
