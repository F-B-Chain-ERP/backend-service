package com.erp.backend_service.repository;

import com.erp.core.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

