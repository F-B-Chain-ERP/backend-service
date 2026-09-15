package com.erp.backend_service.repository;

import com.erp.core.domain.BranchProductAvailability;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchProductAvailabilityRepository extends JpaRepository<BranchProductAvailability, UUID> {

    Optional<BranchProductAvailability> findByBranchIdAndProductId(UUID branchId, UUID productId);

    @Query("""
        SELECT bpa FROM BranchProductAvailability bpa
        JOIN Product p ON p.id = bpa.productId
        WHERE bpa.branchId = :branchId
        AND (:search IS NULL OR :search = ''
            OR LOWER(p.code) LIKE CONCAT('%', LOWER(:search), '%')
            OR LOWER(p.name) LIKE CONCAT('%', LOWER(:search), '%'))
        AND (:categoryId IS NULL OR p.categoryId = :categoryId)
        AND (:status IS NULL OR bpa.status = :status)
        ORDER BY bpa.createdAt DESC
    """)
    Page<BranchProductAvailability> search(
            @Param("branchId") UUID branchId,
            @Param("search") String search,
            @Param("status") String status,
            @Param("categoryId") UUID categoryId,
            Pageable pageable
    );

    @Query("""
        SELECT bpa FROM BranchProductAvailability bpa
        WHERE bpa.branchId = :branchId
        AND bpa.productId IN :productIds
    """)
    List<BranchProductAvailability> findByBranchIdAndProductIds(
            @Param("branchId") UUID branchId,
            @Param("productIds") Collection<UUID> productIds
    );
    Optional<BranchProductAvailability> findByBranchIdAndProductIdAndStatus(UUID branchId, UUID productId,
                                                                            String status);
}
