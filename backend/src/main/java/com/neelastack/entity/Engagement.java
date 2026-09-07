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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// @Data's generated toString()/equals()/hashCode() would touch the three lazy @ManyToOne
// associations below (client, inquiry, project) -- risking a LazyInitializationException
// the moment one of them is uninitialized and this entity gets logged, or is compared via
// equals()/hashCode() outside an open session. Excluding them from @ToString and basing
// equality on just the id avoids that.
@Entity
@Table(name = "engagements")
@Getter
@Setter
@ToString(exclude = {"client", "inquiry", "project"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Engagement {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inquiry_id")
    private Inquiry inquiry;

    /** Set by an admin once this engagement has been written up as a public case
     *  study. Used only to route the post-payment testimonial request (see
     *  TestimonialService) to that project's reviews; null is the normal state
     *  for most engagements and never blocks anything. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EngagementStatus status = EngagementStatus.ONBOARDING;

    private LocalDate startDate;

    private LocalDate targetEndDate;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
