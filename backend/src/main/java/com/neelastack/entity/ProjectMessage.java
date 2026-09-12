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

import java.time.LocalDateTime;
import java.util.UUID;

// Same reasoning as ProjectFile/Engagement: `engagement`, `sender`, and `attachment` are all
// lazy @ManyToOne, so they're excluded from @ToString to avoid a LazyInitializationException
// the moment an uninitialized instance gets logged outside an open session.
@Entity
@Table(name = "project_messages")
@Getter
@Setter
@ToString(exclude = {"engagement", "sender", "attachment"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMessage {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "engagement_id", nullable = false)
    private Engagement engagement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    // Optional: reuses the existing ProjectFile upload pipeline rather than a second one.
    // Nullable both in the DB and here -- most messages are plain text.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_file_id")
    private ProjectFile attachment;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
