package com.erp.backend_service.repository;

import com.erp.core.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderIdAndStatusOrderByCreatedAtAsc(UUID orderId, String status);
}
