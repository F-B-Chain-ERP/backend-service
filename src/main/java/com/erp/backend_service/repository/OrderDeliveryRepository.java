package com.erp.backend_service.repository;

import com.erp.core.domain.OrderDelivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface OrderDeliveryRepository extends JpaRepository<OrderDelivery, UUID> {
    Optional<OrderDelivery> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from OrderDelivery d where d.orderId = :orderId")
    Optional<OrderDelivery> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    /** Số đơn đang giữ của shipper (để auto-assign chọn người rảnh nhất). */
    long countByShipperIdAndStatusIn(UUID shipperId, Collection<String> statuses);

    @Query("""
        select d.shipperId, count(d) from OrderDelivery d
        where d.shipperId in :shipperIds and d.status in :statuses
        group by d.shipperId
        """)
    java.util.List<Object[]> countBusyByShipperIds(@Param("shipperIds") Collection<UUID> shipperIds,
                                                    @Param("statuses") Collection<String> statuses);
}
