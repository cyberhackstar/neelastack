package com.neelastack.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank String token,
        @NotBlank
        @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter and one digit"
        )
        String password,
        // Optional: lets the client correct the name an admin guessed (inquiry name, or the
        // email's local part) at invite time. Blank/absent leaves the existing name as-is.
        String fullName
) {}
