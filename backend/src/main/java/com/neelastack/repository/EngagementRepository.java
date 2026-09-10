package com.neelastack.repository;

import com.neelastack.entity.Engagement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EngagementRepository extends JpaRepository<Engagement, UUID> {
    List<Engagement> findByClientIdOrderByCreatedAtDesc(UUID clientId);
    List<Engagement> findAllByOrderByCreatedAtDesc();

    @Query("SELECT e.status AS status, COUNT(e) AS total FROM Engagement e GROUP BY e.status")
    List<StatusCount> countByStatus();

    /** New clients (engagements created) within a date range — the final stage of the
     *  booking-engine sales funnel, see BookingAnalyticsService. */
    long countByCreatedAtBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);

    interface StatusCount {
        String getStatus();
        Long getTotal();
    }
}
