package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * OAuth tokens for the admin's connected Google Calendar. Optional feature, off by
 * default -- see GoogleCalendarService and app.google-calendar.enabled.
 */
@Entity
@Table(name = "calendar_connections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarConnection {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String provider = "GOOGLE";

    @Column(name = "connected_email", length = 180)
    private String connectedEmail;

    @Column(name = "calendar_id", nullable = false, length = 200)
    @Builder.Default
    private String calendarId = "primary";

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_expires_at", nullable = false)
    private LocalDateTime tokenExpiresAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
