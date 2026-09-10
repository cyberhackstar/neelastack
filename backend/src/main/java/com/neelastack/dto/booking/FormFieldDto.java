package com.neelastack.dto.booking;

import com.neelastack.entity.FormFieldType;
import lombok.Builder;

import java.util.List;

@Builder
public record FormFieldDto(
        String fieldKey,
        String label,
        FormFieldType fieldType,
        Boolean isRequired,
        List<String> options,
        Integer sortOrder
) {}
