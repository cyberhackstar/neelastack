package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One unused one-time MFA recovery code, hashed the same way as login passwords.
 * Single-use: MfaService deletes the row on successful consumption rather than
 * flipping a "used" flag (V23), so the live rows for a user are always exactly the
 * codes still available.
 */
@Entity
@Table(name = "mfa_recovery_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaRecoveryCode {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
