package com.neelastack.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record OAuthExchangeRequest(
        @NotBlank String code
) {}
