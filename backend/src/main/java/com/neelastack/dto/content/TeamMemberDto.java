package com.neelastack.dto.content;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record TeamMemberDto(
        UUID id,
        String name,
        String role,
        String bio,
        List<String> skills,
        String photoUrl,
        int sortOrder,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
