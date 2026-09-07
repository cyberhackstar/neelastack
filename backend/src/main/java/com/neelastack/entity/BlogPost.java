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

import java.time.LocalDateTime;
import java.util.UUID;

// Plain @Data would generate toString()/equals()/hashCode() over every field, including
// the lazy `tags` @ElementCollection below -- so logging a BlogPost, or putting one in a
// Set/Map, outside an open Hibernate session would throw the exact LazyInitializationException
// this codebase already had to fix once in BlogPostService's DTO mapping. Excluding `tags`
// from @ToString, and basing equals()/hashCode() on just the id, removes that risk.
@Entity
@Table(name = "blog_posts")
@Getter
@Setter
@ToString(exclude = "tags")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogPost {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(nullable = false, length = 320)
    private String excerpt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 300)
    private String coverImageUrl;

    @Column(length = 100)
    private String authorName;

    @Column(length = 80)
    private String category;

    @ElementCollection
    @CollectionTable(name = "blog_post_tags", joinColumns = @JoinColumn(name = "blog_post_id"))
    @Column(name = "tag", length = 60)
    @Builder.Default
    private java.util.List<String> tags = java.util.List.of();

    @Column(nullable = false, length = 160)
    private String metaTitle;

    @Column(nullable = false, length = 320)
    private String metaDescription;

    @Builder.Default
    private boolean published = false;

    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
