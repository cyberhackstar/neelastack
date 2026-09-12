package com.neelastack.controller;

import com.neelastack.dto.upi.UpiPaymentMethodDto;
import com.neelastack.dto.upi.UpiPaymentMethodRequest;
import com.neelastack.dto.upi.UpiSubmissionDto;
import com.neelastack.dto.upi.UpiVerificationRequest;
import com.neelastack.service.UpiPaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Requires ROLE_ADMIN (enforced globally for /api/v1/admin/** — see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/upi")
@RequiredArgsConstructor
@Tag(name = "Admin — direct UPI payments", description = "Manage QR payment methods and verify client payment claims")
public class AdminUpiPaymentController {

    private final UpiPaymentService upiPaymentService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @GetMapping("/methods")
    public List<UpiPaymentMethodDto> listMethods() {
        return upiPaymentService.listAllForAdmin();
    }

    // multipart form-data with a "request" JSON part (mirrors ProjectFileService-style
    // upload endpoints elsewhere in this codebase) plus a "qrImage" file part.
    @SneakyThrows
    @PostMapping(value = "/methods", consumes = "multipart/form-data")
    public ResponseEntity<UpiPaymentMethodDto> createMethod(@RequestPart("request") String requestJson,
                                                             @RequestPart("qrImage") MultipartFile qrImage) {
        UpiPaymentMethodRequest request = objectMapper.readValue(requestJson, UpiPaymentMethodRequest.class);
        validateOrThrow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(upiPaymentService.createMethod(request, qrImage));
    }

    // objectMapper.readValue() bypasses @Valid/Bean Validation entirely because Spring only
    // triggers it on the standard @RequestBody binding path, not on a manually-deserialized
    // multipart JSON part. Validating explicitly here closes that gap (security review P1 #4)
    // without restructuring the multipart contract.
    private <T> void validateOrThrow(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    @PostMapping("/methods/{id}/activate")
    public UpiPaymentMethodDto activate(@PathVariable UUID id) {
        return upiPaymentService.setActive(id, true);
    }

    @PostMapping("/methods/{id}/deactivate")
    public UpiPaymentMethodDto deactivate(@PathVariable UUID id) {
        return upiPaymentService.setActive(id, false);
    }

    @DeleteMapping("/methods/{id}")
    public ResponseEntity<Void> deleteMethod(@PathVariable UUID id) {
        upiPaymentService.deleteMethod(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/submissions/pending")
    public List<UpiSubmissionDto> pending() {
        return upiPaymentService.listPending();
    }

    @PostMapping("/submissions/{id}/verify")
    public UpiSubmissionDto verify(@PathVariable UUID id, @RequestBody UpiVerificationRequest request) {
        return request.approve()
                ? upiPaymentService.approve(id, request.adminNote())
                : upiPaymentService.reject(id, request.adminNote());
    }
}
