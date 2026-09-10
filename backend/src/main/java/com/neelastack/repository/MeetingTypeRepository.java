package com.neelastack.repository;

import com.neelastack.entity.MeetingType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingTypeRepository extends JpaRepository<MeetingType, UUID> {
    Optional<MeetingType> findBySlug(String slug);
    List<MeetingType> findByIsActiveTrueOrderBySortOrderAsc();
    List<MeetingType> findAllByOrderBySortOrderAsc();
    boolean existsBySlugAndIdNot(String slug, UUID id);
    boolean existsBySlug(String slug);
}
