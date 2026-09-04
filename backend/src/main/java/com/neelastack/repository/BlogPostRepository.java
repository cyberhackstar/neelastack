package com.neelastack.repository;

import com.neelastack.entity.BlogPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {
    Page<BlogPost> findByPublishedTrueOrderByPublishedAtDesc(Pageable pageable);
    Optional<BlogPost> findBySlugAndPublishedTrue(String slug);
    boolean existsBySlug(String slug);

    @Query("""
            SELECT DISTINCT p FROM BlogPost p LEFT JOIN p.tags t
            WHERE p.published = true
            AND (:q IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(p.excerpt) LIKE LOWER(CONCAT('%', :q, '%')))
            AND (:tag IS NULL OR LOWER(t) = LOWER(:tag))
            ORDER BY p.publishedAt DESC
            """)
    Page<BlogPost> search(@Param("q") String query, @Param("tag") String tag, Pageable pageable);

    List<BlogPost> findTop3ByCategoryAndIdNotAndPublishedTrueOrderByPublishedAtDesc(String category, UUID excludeId);
}
