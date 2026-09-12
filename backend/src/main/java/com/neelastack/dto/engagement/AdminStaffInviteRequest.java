package com.neelastack.dto.engagement;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminStaffInviteRequest(
        @NotBlank @Size(max=120) String fullName,
        @NotBlank @Email @Size(max=180) String email
) {}
