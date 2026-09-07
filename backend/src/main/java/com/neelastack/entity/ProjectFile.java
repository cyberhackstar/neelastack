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

// See BlogPost/Engagement for why @Data is avoided here: `engagement` and `uploadedBy`
// are both lazy @ManyToOne, and @Data's generated toString()/equals()/hashCode() would
// touch both.
@Entity
@Table(name = "project_files")
@Getter
@Setter
@ToString(exclude = {"engagement", "uploadedBy"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectFile {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "engagement_id", nullable = false)
    private Engagement engagement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 500)
    private String fileUrl;

    @Column(nullable = false, length = 255)
    private String cloudinaryPublicId;

    /** "image" or "raw" — Cloudinary needs this to build a correctly-signed delivery URL later. */
    @Column(nullable = false, length = 20)
    private String cloudinaryResourceType;

    @Column(length = 100)
    private String fileType;

    private Long fileSizeBytes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
