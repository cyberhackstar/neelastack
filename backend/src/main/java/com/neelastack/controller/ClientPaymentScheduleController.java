package com.neelastack.controller;

import com.neelastack.dto.paymentschedule.PaymentScheduleDto;
import com.neelastack.service.PaymentScheduleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Authenticated client — ownership enforced via EngagementService#getEntityWithAccessCheck
 *  inside PaymentScheduleService, same pattern as every other client engagement endpoint. */
@RestController
@RequestMapping("/api/v1/client/engagements/{engagementId}/payment-schedule")
@RequiredArgsConstructor
@Tag(name = "Client — payment schedule")
public class ClientPaymentScheduleController {

    private final PaymentScheduleService paymentScheduleService;

    @GetMapping
    public ResponseEntity<PaymentScheduleDto> get(@PathVariable UUID engagementId) {
        PaymentScheduleDto schedule = paymentScheduleService.getForEngagementOrNull(engagementId);
        return schedule == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(schedule);
    }
}
