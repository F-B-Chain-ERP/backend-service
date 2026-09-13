package com.erp.backend_service.repository;

import com.erp.core.domain.OrderItemTopping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderItemToppingRepository extends JpaRepository<OrderItemTopping, UUID> {
    List<OrderItemTopping> findByOrderItemIdAndStatus(UUID orderItemId, String status);
}
