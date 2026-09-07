package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// User has no lazy relations of its own, so it wasn't at risk of the
// LazyInitializationException the other entities in this package were fixed for -- but
// plain @Data would still put `password` (a bcrypt hash) and `totpSecret` (AES-GCM
// ciphertext) into the generated toString(), so any accidental log.debug("{}", user) or
// exception message built from `"" + user` would leak credential material into logs.
// Excluding them here is defense in depth even though neither is plaintext.
@Entity
@Table(name = "users")
@Getter
@Setter
@ToString(exclude = {"password", "totpSecret"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.CLIENT;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private boolean emailVerified = false;

    // --- MFA (Section 2) ---

    @Builder.Default
    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    /** AES-256-GCM ciphertext (see TotpEncryptionService) -- never plaintext. Null until enrolled. */
    @Column(name = "totp_secret", length = 500)
    private String totpSecret;

    @Column(name = "mfa_enrolled_at")
    private LocalDateTime mfaEnrolledAt;

    // --- Security-event bookkeeping (P0 hardening) ---

    /** Forced true for a freshly-bootstrapped admin or a force-reset account; blocks every route except password change. */
    @Builder.Default
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    /**
     * Bumped on password reset and MFA disable/force-reset. Embedded in every JWT as "tv";
     * a token whose "tv" doesn't match this value is rejected, which is what invalidates
     * every previously-issued access/refresh token across all devices on a security event.
     */
    @Builder.Default
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
