package com.neelastack.dto.booking;

import com.neelastack.entity.FormFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FormFieldRequest(
        @NotBlank @Size(max = 60) String fieldKey,
        @NotBlank @Size(max = 200) String label,
        @NotNull FormFieldType fieldType,
        Boolean isRequired,
        List<String> options,
        Integer sortOrder
) {}
