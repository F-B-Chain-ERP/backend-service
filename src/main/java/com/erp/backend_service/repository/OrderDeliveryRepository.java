package com.erp.backend_service.repository;

import com.erp.core.domain.OrderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface OrderDeliveryRepository extends JpaRepository<OrderDelivery, UUID> {
    Optional<OrderDelivery> findByOrderId(UUID orderId);

    /** Số đơn đang giữ của shipper (để auto-assign chọn người rảnh nhất). */
    long countByShipperIdAndStatusIn(UUID shipperId, Collection<String> statuses);
}
