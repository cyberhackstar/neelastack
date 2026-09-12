package com.neelastack.controller;

import com.neelastack.dto.paymentschedule.PaymentScheduleDto;
import com.neelastack.dto.paymentschedule.PaymentScheduleInstallmentDto;
import com.neelastack.dto.paymentschedule.PaymentScheduleRequest;
import com.neelastack.service.PaymentScheduleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Requires ROLE_ADMIN (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/payment-schedules")
@RequiredArgsConstructor
@Tag(name = "Admin — payment schedules", description = "Deposit/milestone-based payment plans for enterprise engagements")
public class AdminPaymentScheduleController {

    private final PaymentScheduleService paymentScheduleService;

    @PostMapping
    public ResponseEntity<PaymentScheduleDto> create(@Valid @RequestBody PaymentScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentScheduleService.create(request));
    }

    @GetMapping("/engagement/{engagementId}")
    public PaymentScheduleDto getForEngagement(@PathVariable UUID engagementId) {
        return paymentScheduleService.getForEngagement(engagementId);
    }

    @PostMapping("/installments/{installmentId}/raise-invoice")
    public PaymentScheduleInstallmentDto raiseInvoice(@PathVariable UUID installmentId) {
        return paymentScheduleService.raiseInvoice(installmentId);
    }
}
