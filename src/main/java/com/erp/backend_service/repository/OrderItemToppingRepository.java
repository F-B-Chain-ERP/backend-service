package com.erp.backend_service.repository;

import com.erp.core.domain.OrderItemTopping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderItemToppingRepository extends JpaRepository<OrderItemTopping, UUID> {
    List<OrderItemTopping> findByOrderItemIdAndStatus(UUID orderItemId, String status);

    /** Bulk topping cả đơn trong 1 query (tránh N+1 ở chi tiết đơn). */
    List<OrderItemTopping> findByOrderItemIdInAndStatus(Collection<UUID> orderItemIds, String status);
}
