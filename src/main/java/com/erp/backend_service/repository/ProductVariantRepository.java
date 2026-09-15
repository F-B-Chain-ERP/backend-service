package com.erp.backend_service.repository;

import com.erp.core.domain.ProductVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    /**
     * Lấy toàn bộ biến thể / kích cỡ của sản phẩm theo displayOrder tăng dần.
     */
    List<ProductVariant> findByProductIdOrderByDisplayOrderAsc(UUID productId);

    /**
     * Lấy các biến thể theo trạng thái (ví dụ ACTIVE) sắp xếp theo displayOrder tăng dần.
     */
    List<ProductVariant> findByProductIdAndStatusOrderByDisplayOrderAsc(UUID productId, String status);

    /**
     * Tìm biến thể theo ID và Product ID.
     */
    Optional<ProductVariant> findByIdAndProductId(UUID id, UUID productId);

    /**
     * Kiểm tra mã biến thể đã tồn tại trong sản phẩm (không phân biệt hoa thường).
     */
    boolean existsByProductIdAndVariantCodeIgnoreCase(UUID productId, String variantCode);

    /**
     * Kiểm tra mã biến thể đã tồn tại trong sản phẩm ngoài ID hiện tại (dùng khi cập nhật).
     */
    boolean existsByProductIdAndVariantCodeIgnoreCaseAndIdNot(UUID productId, String variantCode, UUID id);

    /**
     * Xóa toàn bộ biến thể của sản phẩm.
     */
    void deleteByProductId(UUID productId);

    /**
     * Đếm số biến thể của sản phẩm.
     */
    long countByProductId(UUID productId);

    /**
     * Truy vấn phân trang danh sách biến thể phục vụ màn hình BOM overview.
     * Lọc theo từ khóa (mã/tên biến thể hoặc mã/tên sản phẩm), danh mục và trạng thái công thức
     * ngay trong SQL để page + total nguyên nhất quán, tránh N+1.
     */
    @Query("""
                SELECT pv
                FROM ProductVariant pv
                WHERE pv.status <> 'DELETED'
                  AND NOT EXISTS (SELECT p FROM Product p WHERE p.id = pv.productId AND p.status = 'DELETED')
                  AND (:search IS NULL OR :search = ''
                       OR LOWER(pv.variantCode) LIKE CONCAT('%', LOWER(:search), '%')
                       OR LOWER(pv.variantName) LIKE CONCAT('%', LOWER(:search), '%')
                       OR EXISTS (SELECT p FROM Product p WHERE p.id = pv.productId
                                  AND (LOWER(p.code) LIKE CONCAT('%', LOWER(:search), '%')
                                    OR LOWER(p.name) LIKE CONCAT('%', LOWER(:search), '%'))))
                  AND (:categoryId IS NULL OR EXISTS (SELECT p FROM Product p
                       WHERE p.id = pv.productId AND p.categoryId = :categoryId))
                  AND (:bomStatus IS NULL OR :bomStatus = ''
                       OR (:bomStatus = 'HAS_BOM' AND EXISTS
                           (SELECT 1 FROM ProductRecipeItem r WHERE r.variantId = pv.id AND r.status = 'ACTIVE'))
                       OR (:bomStatus = 'NO_BOM' AND NOT EXISTS
                           (SELECT 1 FROM ProductRecipeItem r WHERE r.variantId = pv.id AND r.status = 'ACTIVE')))
            """)
    Page<ProductVariant> searchForBomOverview(
            @Param("search") String search,
            @Param("categoryId") UUID categoryId,
            @Param("bomStatus") String bomStatus,
            Pageable pageable
    );
}

