package com.neelastack.repository;

import com.neelastack.entity.BookingFormField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingFormFieldRepository extends JpaRepository<BookingFormField, UUID> {
    List<BookingFormField> findByMeetingTypeIdOrderBySortOrderAsc(UUID meetingTypeId);
    void deleteByMeetingTypeId(UUID meetingTypeId);
}
