package com.erp.backend_service.repository;

import com.erp.core.domain.KdsTicket;
import com.erp.core.domain.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
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
        return filter(branchId, status, fromDate, toDate, null);
    }

    /**
     * Search KDS theo mã đơn (orders.order_code) hoặc mã ticket (station-queueNo).
     * ticketCode không lưu DB mà ghép từ station + queueNo (VD BAR-012) nên:
     * - "BAR-012" -&gt; station=BAR AND queueNo=12
     * - "012"/"12" -&gt; queueNo=12 OR orderCode like
     * - còn lại -&gt; orderCode like (case-insensitive) OR station like
     * Order chỉ join qua subquery vì KdsTicket lưu orderId thô (không @ManyToOne).
     */
    public static Specification<KdsTicket> filter(UUID branchId, String status,
                                                  Instant fromDate, Instant toDate, String search) {
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
            if (search != null && !search.isBlank()) {
                String s = search.trim();
                String pattern = "%" + s.toLowerCase() + "%";
                List<Predicate> orParts = new ArrayList<>();
                // 1. orderCode via subquery.
                Subquery<UUID> orderIds = query.subquery(UUID.class);
                var orderRoot = orderIds.from(Order.class);
                orderIds.select(orderRoot.get("id"));
                orderIds.where(cb.like(cb.lower(orderRoot.get("orderCode")), pattern));
                orParts.add(root.get("orderId").in(orderIds));
                // 2. ticketCode parsing.
                if (s.contains("-")) {
                    String[] parts = s.split("-", 2);
                    String stationPart = parts[0].trim();
                    String noPart = parts[1].trim();
                    if (!stationPart.isEmpty() && !noPart.isEmpty()) {
                        try {
                            int queueNo = Integer.parseInt(noPart.replaceFirst("^0+(?!$)", ""));
                            orParts.add(cb.and(
                                cb.equal(cb.upper(root.get("station")), stationPart.toUpperCase()),
                                cb.equal(root.get("queueNo"), queueNo)));
                        } catch (NumberFormatException ignored) {
                            orParts.add(cb.like(cb.lower(root.get("station")), "%" + stationPart.toLowerCase() + "%"));
                        }
                    }
                } else {
                    try {
                        int queueNo = Integer.parseInt(s.replaceFirst("^0+(?!$)", ""));
                        orParts.add(cb.equal(root.get("queueNo"), queueNo));
                    } catch (NumberFormatException ignored) {
                        // không phải số: bỏ qua nhánh queueNo
                    }
                    orParts.add(cb.like(cb.upper(root.get("station")), "%" + s.toUpperCase() + "%"));
                }
                predicates.add(cb.or(orParts.toArray(new Predicate[0])));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
