package com.neelastack.repository;

import com.neelastack.entity.UpiPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UpiPaymentMethodRepository extends JpaRepository<UpiPaymentMethod, UUID> {
    List<UpiPaymentMethod> findAllByOrderByDisplayOrderAsc();
    List<UpiPaymentMethod> findByActiveTrueOrderByDisplayOrderAsc();
}
