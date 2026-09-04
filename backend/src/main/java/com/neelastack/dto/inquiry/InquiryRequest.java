package com.neelastack.dto.inquiry;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 20) String phone,
        @Size(max = 120) String company,
        @Size(max = 80) String projectType,
        @Size(max = 60) String budgetRange,
        @NotBlank @Size(max = 4000) String message
) {}
