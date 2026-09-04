package com.neelastack.repository;

import com.neelastack.entity.Review;
import com.neelastack.entity.ReviewSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    List<Review> findByProjectIdAndPublishedTrueOrderByDisplayOrderAsc(UUID projectId);

    List<Review> findByProjectIdOrderByDisplayOrderAsc(UUID projectId);

    @Query("select avg(r.rating) from Review r where r.projectId = :projectId and r.published = true")
    Optional<Double> findAverageRatingByProjectId(@Param("projectId") UUID projectId);

    long countByProjectIdAndPublishedTrue(UUID projectId);

    /** Module 4 moderation queue: client-submitted testimonials an admin hasn't
     *  published yet (regardless of whether a project has been assigned). */
    List<Review> findBySubmittedViaAndPublishedFalseOrderByCreatedAtDesc(ReviewSource submittedVia);
}
