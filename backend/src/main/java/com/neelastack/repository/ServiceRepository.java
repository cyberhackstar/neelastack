package com.neelastack.repository;

import com.neelastack.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceRepository extends JpaRepository<Service, UUID> {
    List<Service> findByPublishedTrueOrderByDisplayOrderAsc();
    Optional<Service> findBySlugAndPublishedTrue(String slug);
    boolean existsBySlug(String slug);
}
