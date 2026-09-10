package com.neelastack.repository;

import com.neelastack.entity.AvailabilityDateOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilityDateOverrideRepository extends JpaRepository<AvailabilityDateOverride, UUID> {
    Optional<AvailabilityDateOverride> findByOverrideDate(LocalDate date);
    List<AvailabilityDateOverride> findByOverrideDateBetweenOrderByOverrideDateAsc(LocalDate from, LocalDate to);
    void deleteByOverrideDate(LocalDate date);
}
