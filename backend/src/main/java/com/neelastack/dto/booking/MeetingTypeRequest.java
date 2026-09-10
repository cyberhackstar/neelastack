package com.neelastack.dto.booking;

import com.neelastack.entity.LocationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record MeetingTypeRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 140) String slug,
        @Size(max = 4000) String description,
        @NotNull @Min(5) @Max(480) Integer durationMinutes,
        @Min(0) @Max(180) Integer bufferBeforeMinutes,
        @Min(0) @Max(180) Integer bufferAfterMinutes,
        @Min(0) @Max(20160) Integer minNoticeMinutes,
        @Min(1) @Max(365) Integer maxHorizonDays,
        @NotNull LocationType locationType,
        @Size(max = 300) String locationDetail,
        BigDecimal price,
        @Size(max = 8) String currency,
        Boolean requiresPayment,
        @Min(0) @Max(720) Integer cancellableUntilHours,
        Boolean isActive,
        Integer sortOrder,
        @Valid List<FormFieldRequest> formFields
) {}
