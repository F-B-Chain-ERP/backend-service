package com.erp.backend_service.repository;

import com.erp.core.domain.ProductRecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Truy vấn dữ liệu định mức nguyên liệu pha chế (product_recipe_item).
 */
@Repository
public interface ProductRecipeItemRepository extends JpaRepository<ProductRecipeItem, UUID> {

    /** Lấy toàn bộ dòng BOM theo_variant, sắp xếp theo thời gian tạo tăng dần. */
    List<ProductRecipeItem> findByVariantIdOrderByCreatedAtAsc(UUID variantId);

    /** Kiểm tra nguyên vật liệu đã tồn tại trong BOM của_variant hay chưa. */
    boolean existsByVariantIdAndMaterialId(UUID variantId, UUID materialId);

    /** Xóa toàn bộ dòng BOM của_variant (dùng khi bulk-replace). */
    void deleteByVariantId(UUID variantId);
}
