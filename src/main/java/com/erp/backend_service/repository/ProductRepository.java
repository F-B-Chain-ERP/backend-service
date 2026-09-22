package com.erp.backend_service.repository;

import com.erp.core.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    long countByCategoryId(UUID categoryId);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    @Query("""
                SELECT p
                FROM Product p
                WHERE (:search IS NULL OR :search = ''
                    OR LOWER(p.code) LIKE CONCAT('%', LOWER(:search), '%')
                    OR LOWER(p.name) LIKE CONCAT('%', LOWER(:search), '%'))
                AND (:categoryId IS NULL OR p.categoryId = :categoryId)
                AND ((:status IS NULL AND p.status <> 'DELETED') OR p.status = :status)
                AND (:isFeatured IS NULL OR p.isFeatured = :isFeatured)
                AND (:isBestSeller IS NULL OR p.isBestSeller = :isBestSeller)
                AND (:isCombo IS NULL OR p.isCombo = :isCombo)
            """)
    Page<Product> search(
            @Param("search") String search,
            @Param("categoryId") UUID categoryId,
            @Param("status") String status,
            @Param("isFeatured") Boolean isFeatured,
            @Param("isBestSeller") Boolean isBestSeller,
            @Param("isCombo") Boolean isCombo,
            Pageable pageable
    );

    @Query("""
                SELECT p
                FROM Product p
                WHERE p.status = 'ACTIVE'
                AND (:search IS NULL OR :search = ''
                    OR LOWER(p.code) LIKE CONCAT('%', LOWER(:search), '%')
                    OR LOWER(p.name) LIKE CONCAT('%', LOWER(:search), '%'))
                AND (:categoryId IS NULL OR p.categoryId = :categoryId)
                AND (:isFeatured IS NULL OR p.isFeatured = :isFeatured)
                AND (:isBestSeller IS NULL OR p.isBestSeller = :isBestSeller)
            """)
    Page<Product> findActiveForSales(
            @Param("search") String search,
            @Param("categoryId") UUID categoryId,
            @Param("isFeatured") Boolean isFeatured,
            @Param("isBestSeller") Boolean isBestSeller,
            Pageable pageable
    );

    /**
     * Biến thể không COUNT(*) của {@link #findActiveForSales} cho kênh bán hàng.
     * Store tải 1 cục, không pager theo total nên không cần totalElements;
     * Slice giúp Spring Data bỏ query COUNT, bớt 1 full-scan mỗi request.
     * Có branchId thì chỉ trả món KHẢ DỤNG tại chi nhánh đó:
     * (chưa có record availability = mặc định bán; chỉ record is_available=false mới ẩn).
     */
    @Query("""
                SELECT p
                FROM Product p
                WHERE p.status = 'ACTIVE'
                AND (:search IS NULL OR :search = ''
                    OR LOWER(p.code) LIKE CONCAT('%', LOWER(:search), '%')
                    OR LOWER(p.name) LIKE CONCAT('%', LOWER(:search), '%'))
                AND (:categoryId IS NULL OR p.categoryId = :categoryId)
                AND (:isFeatured IS NULL OR p.isFeatured = :isFeatured)
                AND (:isBestSeller IS NULL OR p.isBestSeller = :isBestSeller)
                AND (:branchId IS NULL OR NOT EXISTS (
                    SELECT bpa
                    FROM BranchProductAvailability bpa
                    WHERE bpa.branchId = :branchId
                    AND bpa.productId = p.id
                    AND bpa.isAvailable = false
                ))
            """)
    Slice<Product> findActiveForSalesSlice(
            @Param("search") String search,
            @Param("categoryId") UUID categoryId,
            @Param("isFeatured") Boolean isFeatured,
            @Param("isBestSeller") Boolean isBestSeller,
            @Param("branchId") UUID branchId,
            Pageable pageable
    );

    @Query("""
                SELECT p
                FROM Product p
                WHERE p.status = 'ACTIVE'
                AND (:search IS NULL OR :search = ''
                    OR LOWER(p.code) LIKE CONCAT('%', LOWER(:search), '%')
                    OR LOWER(p.name) LIKE CONCAT('%', LOWER(:search), '%'))
                AND (:categoryId IS NULL OR p.categoryId = :categoryId)
            """)
    Page<Product> findActiveForAvailability(
            @Param("search") String search,
            @Param("categoryId") UUID categoryId,
            Pageable pageable
    );
}
