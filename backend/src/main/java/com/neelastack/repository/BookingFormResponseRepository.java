package com.neelastack.repository;

import com.neelastack.entity.BookingFormResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingFormResponseRepository extends JpaRepository<BookingFormResponse, UUID> {
    List<BookingFormResponse> findByBookingId(UUID bookingId);
}
