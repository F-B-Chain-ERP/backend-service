package com.erp.backend_service.repository;

import com.erp.core.domain.Order;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filter động cho màn staff quản đơn.
 * Dùng Specification thay cho @Query "? is null" vì Postgres không đoán được
 * kiểu của param Instant null -> 500 "could not determine data type of parameter".
 */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> filter(UUID branchId, UUID customerId, String orderType, String status,
                                              Instant fromDate, Instant toDate) {
        return filter(branchId, customerId, orderType, status, fromDate, toDate, null);
    }

    public static Specification<Order> filter(UUID branchId, UUID customerId, String orderType, String status,
                                              Instant fromDate, Instant toDate, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (orderType != null && !orderType.isBlank()) {
                predicates.add(cb.equal(root.get("orderType"), orderType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThan(root.<Instant>get("createdAt"), toDate));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderCode")), pattern),
                        cb.like(cb.lower(root.get("customerName")), pattern),
                        cb.like(cb.lower(root.get("customerPhone")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
