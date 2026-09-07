package com.erp.backend_service.mapper;

import com.erp.core.domain.Category;
import com.erp.core.dto.request.menu.CreateCategoryRequest;
import com.erp.core.dto.request.menu.UpdateCategoryRequest;
import com.erp.core.dto.response.menu.CategoryResponse;
import org.springframework.stereotype.Component;

/**
 * Ánh xạ thủ công giữa {@link Category} và các DTO danh mục.
 */
@Component
public class CategoryMapper {

    /** Ánh xạ entity sang response (không đếm con, usedCount = 0). */
    public CategoryResponse toResponse(Category e) {
        return toResponse(e, 0L);
    }

    /** Ánh xạ entity sang response kèm số món-NVL đang dùng (BA-02 F-C01). */
    public CategoryResponse toResponse(Category e, long usedCount) {
        return new CategoryResponse(
                e.getId() != null ? e.getId().toString() : null,
                e.getCategoryType(),
                e.getCode(),
                e.getName(),
                e.getDescription(),
                e.getImageUrl(),
                e.getDisplayOrder(),
                e.getStatus(),
                usedCount,
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedBy(),
                e.getUpdatedAt());
    }

    /** Tạo Category mới từ CreateCategoryRequest (mặc định ACTIVE). */
    public Category toEntity(CreateCategoryRequest request) {
        Category c = new Category();
        c.setCategoryType(request.categoryType());
        c.setCode(request.code());
        c.setName(request.name());
        c.setDescription(request.description());
        c.setImageUrl(request.imageUrl());
        c.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
        c.setStatus("ACTIVE");
        return c;
    }

    /** Cập nhật Category từ UpdateCategoryRequest (không đổi status). */
    public void updateEntity(Category c, UpdateCategoryRequest request) {
        c.setCategoryType(request.categoryType());
        c.setCode(request.code());
        c.setName(request.name());
        c.setDescription(request.description());
        c.setImageUrl(request.imageUrl());
        c.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
    }
}
