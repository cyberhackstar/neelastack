package com.neelastack.repository;

import com.neelastack.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByPublishedTrueOrderByDisplayOrderAsc();
    List<Project> findByPublishedTrueAndFeaturedTrueOrderByDisplayOrderAsc();
    Optional<Project> findBySlugAndPublishedTrue(String slug);
    boolean existsBySlug(String slug);

    /**
     * Best available published case study for a service category, for the
     * proposal-injection feature (module 3). Featured first, then most recently
     * created, so a fresher/stronger proof point is preferred when several
     * projects share a tag. Returns nothing (not a fallback project) when no
     * published, tagged case study exists — the caller must treat an empty
     * result as "show no case study", never substitute an unrelated one.
     */
    @Query("""
            SELECT p FROM Project p JOIN p.serviceCategories c
            WHERE p.published = true AND lower(c) = lower(:category)
            ORDER BY p.featured DESC, p.createdAt DESC
            """)
    List<Project> findBestMatchesForCategory(@Param("category") String category);
}
