package com.neelastack.controller;

import com.neelastack.dto.mfa.*;
import com.neelastack.security.CurrentUserProvider;
import com.neelastack.service.MfaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * All endpoints here require ROLE_ADMIN (see SecurityConfig's /api/v1/admin/** rule).
 * /setup, /verify, /disable, /recovery, /step-up act on the calling admin's own
 * account (via CurrentUserProvider) -- there is no "manage someone else's MFA"
 * endpoint except /force-reset, which is a distinct, more dangerous operation.
 */
@RestController
@RequestMapping("/api/v1/admin/mfa")
@RequiredArgsConstructor
@Tag(name = "Admin — MFA", description = "TOTP-based multi-factor auth for admin accounts — requires ROLE_ADMIN")
public class MfaController {

    private final MfaService mfaService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/status")
    public MfaStatusResponse status() {
        return mfaService.status(currentUserProvider.get());
    }

    @PostMapping("/setup")
    public MfaSetupResponse setup() {
        return mfaService.setup(currentUserProvider.get());
    }

    @PostMapping("/verify")
    public MfaVerifyResponse verify(@Valid @RequestBody MfaVerifyRequest request) {
        return mfaService.verify(currentUserProvider.get(), request.code());
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@Valid @RequestBody MfaDisableRequest request) {
        mfaService.disable(currentUserProvider.get(), request.password(), request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recovery")
    public ResponseEntity<Void> recovery(@Valid @RequestBody MfaRecoveryRequest request) {
        mfaService.consumeRecoveryCode(currentUserProvider.get(), request.recoveryCode());
        return ResponseEntity.noContent().build();
    }

    /** Refreshes the step-up assertion checked by StepUpAuthFilter on high-risk mutation endpoints. */
    @PostMapping("/step-up")
    public ResponseEntity<Void> stepUp(@Valid @RequestBody MfaVerifyRequest request) {
        mfaService.stepUp(currentUserProvider.get(), request.code());
        return ResponseEntity.noContent().build();
    }

    /**
     * Out-of-band recovery for an admin locked out of their own MFA (lost device, no
     * recovery codes). See MfaService#forceReset for the caveat: this codebase has no
     * separate superadmin role yet, so today this is reachable by any ROLE_ADMIN, not
     * just a superadmin — the endpoint is still step-up gated and audit logged, but the
     * role restriction itself is a follow-up, not something this pass could safely add
     * without touching the whole authorization model.
     */
    @PostMapping("/{userId}/force-reset")
    public ResponseEntity<Void> forceReset(@PathVariable UUID userId) {
        mfaService.forceReset(userId, currentUserProvider.get());
        return ResponseEntity.noContent().build();
    }
}
