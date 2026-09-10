package com.neelastack.dto.booking;

import jakarta.validation.constraints.Size;

public record CancelRequest(
        @Size(max = 500) String reason
) {}
