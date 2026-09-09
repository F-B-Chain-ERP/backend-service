package com.erp.backend_service.repository;

import com.erp.core.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
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
}
