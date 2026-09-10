package com.neelastack.dto.booking;

import com.neelastack.entity.LocationType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record MeetingTypeDto(
        UUID id,
        String name,
        String slug,
        String description,
        Integer durationMinutes,
        Integer bufferBeforeMinutes,
        Integer bufferAfterMinutes,
        Integer minNoticeMinutes,
        Integer maxHorizonDays,
        LocationType locationType,
        String locationDetail,
        BigDecimal price,
        String currency,
        Boolean requiresPayment,
        Integer cancellableUntilHours,
        Boolean isActive,
        Integer sortOrder,
        List<FormFieldDto> formFields,
        LocalDateTime createdAt
) {}
