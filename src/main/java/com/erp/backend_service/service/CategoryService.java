package com.erp.backend_service.service;

import com.erp.core.dto.request.menu.CreateCategoryRequest;
import com.erp.core.dto.request.menu.UpdateCategoryRequest;
import com.erp.core.dto.response.menu.CategoryResponse;
import com.erp.core.dto.response.PageResponse;

import java.util.UUID;

public interface CategoryService {

    PageResponse<CategoryResponse> list(int page, int size, String search, String categoryType, String status);

    CategoryResponse get(UUID id);

    CategoryResponse create(CreateCategoryRequest request);

    CategoryResponse update(UUID id, UpdateCategoryRequest request);

    CategoryResponse updateStatus(UUID id, String status);

    void delete(UUID id);
}
