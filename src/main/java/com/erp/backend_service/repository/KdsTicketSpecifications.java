package com.erp.backend_service.repository;

import com.erp.core.domain.KdsTicket;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filter động cho màn bếp KDS.
 * Dùng Specification thay cho @Query "? is null" vì Postgres không đoán được
 * kiểu của param null (UUID/Instant) -> 500 "could not determine data type".
 */
public final class KdsTicketSpecifications {

    private KdsTicketSpecifications() {
    }

    public static Specification<KdsTicket> filter(UUID branchId, String status,
                                                  Instant fromDate, Instant toDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
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
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
