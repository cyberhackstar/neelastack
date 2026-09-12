package com.neelastack.dto.engagement;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record ProjectTaskRequest(
        @NotBlank String title,
        String description,
        LocalDate dueDate,
        Boolean clientActionRequired,
        Integer displayOrder
) {}
