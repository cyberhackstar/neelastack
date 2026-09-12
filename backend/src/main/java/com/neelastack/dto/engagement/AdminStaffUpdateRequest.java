package com.neelastack.dto.engagement;

import com.neelastack.entity.Role;
import jakarta.validation.constraints.NotNull;

public record AdminStaffUpdateRequest(@NotNull Role role, boolean enabled) {}
