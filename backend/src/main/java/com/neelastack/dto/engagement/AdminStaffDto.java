package com.neelastack.dto.engagement;

import com.neelastack.entity.Role;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record AdminStaffDto(UUID id, String fullName, String email, Role role, boolean enabled,
                            boolean mfaEnabled, boolean mustChangePassword, LocalDateTime createdAt) {}
