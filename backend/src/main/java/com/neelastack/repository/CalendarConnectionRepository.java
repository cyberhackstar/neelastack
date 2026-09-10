package com.neelastack.repository;

import com.neelastack.entity.CalendarConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CalendarConnectionRepository extends JpaRepository<CalendarConnection, UUID> {
    Optional<CalendarConnection> findFirstByIsActiveTrueOrderByCreatedAtDesc();
}
