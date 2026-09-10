package com.neelastack.dto.booking;

import com.neelastack.entity.BookingOutcome;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookingOutcomeRequest(
        @NotNull BookingOutcome outcome,
        @Size(max = 4000) String internalNotes
) {}
