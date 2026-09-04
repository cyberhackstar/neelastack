package com.neelastack.dto.engagement;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record MilestoneRequest(
        @NotBlank String title,
        String description,
        LocalDate dueDate,
        Integer displayOrder
) {}
