package com.neelastack.repository;

import com.neelastack.entity.AvailabilityWindow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AvailabilityWindowRepository extends JpaRepository<AvailabilityWindow, UUID> {
    List<AvailabilityWindow> findByIsActiveTrue();
    List<AvailabilityWindow> findByDayOfWeekAndIsActiveTrue(Integer dayOfWeek);
    List<AvailabilityWindow> findAllByOrderByDayOfWeekAscStartTimeAsc();
}
