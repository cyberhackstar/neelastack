package com.neelastack.repository;

import com.neelastack.entity.TechStackPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TechStackPageRepository extends JpaRepository<TechStackPage, UUID> {
    List<TechStackPage> findByPublishedTrueOrderByDisplayOrderAsc();
    Optional<TechStackPage> findBySlugAndPublishedTrue(String slug);
    boolean existsBySlug(String slug);
}
