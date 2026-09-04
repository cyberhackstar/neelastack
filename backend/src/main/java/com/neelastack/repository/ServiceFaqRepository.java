package com.neelastack.repository;

import com.neelastack.entity.ServiceFaq;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceFaqRepository extends JpaRepository<ServiceFaq, UUID> {
    List<ServiceFaq> findByServiceIdOrderByDisplayOrderAsc(UUID serviceId);

    void deleteByServiceId(UUID serviceId);
}
