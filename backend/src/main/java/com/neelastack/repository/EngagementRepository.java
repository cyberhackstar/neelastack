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

    interface StatusCount {
        String getStatus();
        Long getTotal();
    }
}
