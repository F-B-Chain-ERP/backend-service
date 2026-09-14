package com.erp.backend_service.repository;

import com.erp.core.domain.ProductRecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRecipeItemRepository extends JpaRepository<ProductRecipeItem, UUID> {

    /**
     * Lấy các dòng công thức theo biến thể và trạng thái (thường là ACTIVE).
     */
    List<ProductRecipeItem> findByVariantIdAndStatusOrderByCreatedAtAsc(UUID variantId, String status);

    /**
     * Lấy toàn bộ dòng công thức của một biến thể (bao gồm cả ACTIVE và INACTIVE).
     */
    List<ProductRecipeItem> findByVariantId(UUID variantId);

    /**
     * Tìm dòng công thức theo ID dòng và variant ID.
     */
    Optional<ProductRecipeItem> findByIdAndVariantId(UUID id, UUID variantId);

    /**
     * Tìm dòng công thức theo variant ID và material ID.
     */
    Optional<ProductRecipeItem> findByVariantIdAndMaterialId(UUID variantId, UUID materialId);

    /**
     * Kiểm tra xem nguyên vật liệu đã có trong công thức với trạng thái chỉ định.
     */
    boolean existsByVariantIdAndMaterialIdAndStatus(UUID variantId, UUID materialId, String status);

    /**
     * Kiểm tra xem nguyên vật liệu đã có trong công thức của biến thể khác dòng ID hiện tại.
     */
    boolean existsByVariantIdAndMaterialIdAndIdNotAndStatus(UUID variantId, UUID materialId, UUID id, String status);

    /**
     * Đếm số lượng nguyên vật liệu đang ACTIVE trong công thức của biến thể.
     */
    long countByVariantIdAndStatus(UUID variantId, String status);

    /**
     * Xóa các dòng công thức theo variantId.
     */
    void deleteByVariantId(UUID variantId);
}
