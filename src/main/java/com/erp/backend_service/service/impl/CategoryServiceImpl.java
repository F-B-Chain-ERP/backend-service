package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.CategoryMapper;
import com.erp.backend_service.repository.CategoryRepository;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.ProductRepository;
import com.erp.backend_service.service.CategoryService;
import com.erp.core.domain.Category;
import com.erp.core.dto.request.menu.CreateCategoryRequest;
import com.erp.core.dto.request.menu.UpdateCategoryRequest;
import com.erp.core.dto.response.menu.CategoryResponse;
import com.erp.core.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * Xử lý nghiệp vụ danh mục (master dùng chung INV + MENU).
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CategoryRepository categoryRepository;
    private final MaterialRepository materialRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(CategoryRepository categoryRepository,
                               MaterialRepository materialRepository,
                               ProductRepository productRepository,
                               CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.materialRepository = materialRepository;
        this.productRepository = productRepository;
        this.categoryMapper = categoryMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> list(int page, int size, String search, String categoryType, String status) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("displayOrder").ascending().and(Sort.by("createdAt").descending()));
        Page<Category> pageResult = categoryRepository.search(
                trimOrNull(search),
                upperOrNull(categoryType),
                upperOrNull(status),
                pageable);
        List<CategoryResponse> items = pageResult.getContent().stream()
                .map(c -> categoryMapper.toResponse(c, countChildren(c.getId())))
                .toList();
        return new PageResponse<>(
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                items);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        return categoryMapper.toResponse(findById(id));
    }

    @Override
    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String categoryType = request.categoryType().trim().toUpperCase();
        String code = request.code().trim().toUpperCase();
        if (categoryRepository.existsByCategoryTypeAndCode(categoryType, code)) {
            throw new BaseException(ErrorCode.MENU_400_CATEGORY_CODE_EXISTED);
        }
        Category category = categoryMapper.toEntity(new CreateCategoryRequest(
                categoryType, code, trimOrNull(request.name()), trimOrNull(request.description()),
                trimOrNull(request.imageUrl()), request.displayOrder()));
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        Category category = findById(id);
        String categoryType = request.categoryType().trim().toUpperCase();
        String code = request.code().trim().toUpperCase();
        if ((!category.getCategoryType().equals(categoryType) || !category.getCode().equals(code))
                && categoryRepository.existsByCategoryTypeAndCodeAndIdNot(categoryType, code, id)) {
            throw new BaseException(ErrorCode.MENU_400_CATEGORY_CODE_EXISTED);
        }
        if (!category.getCategoryType().equals(categoryType) && countChildren(id) > 0) {
            throw new BaseException(ErrorCode.MENU_400_CATEGORY_IN_USE,
                    "Danh mục đang được sử dụng, không thể đổi loại. Vui lòng chuyển sang INACTIVE.");
        }
        categoryMapper.updateEntity(category, new UpdateCategoryRequest(
                categoryType, code, trimOrNull(request.name()), trimOrNull(request.description()),
                trimOrNull(request.imageUrl()), request.displayOrder()));
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse updateStatus(UUID id, String status) {
        Category category = findById(id);
        if (!StringUtils.hasText(status)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        String normalizedStatus = status.trim().toUpperCase();
        if (!"ACTIVE".equals(normalizedStatus) && !"INACTIVE".equals(normalizedStatus)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        category.setStatus(normalizedStatus);
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Category category = findById(id);
        long children = countChildren(id);
        if (children > 0) {
            String childLabel = "PRODUCT".equals(category.getCategoryType()) ? "sản phẩm" : "nguyên vật liệu";
            throw new BaseException(ErrorCode.MENU_400_CATEGORY_IN_USE,
                    "Danh mục còn " + children + " " + childLabel + ", không thể xóa. "
                            + "Vui lòng chuyển sang danh mục khác hoặc ngừng sử dụng.");
        }
        categoryRepository.deleteById(id);
    }

    private long countChildren(UUID categoryId) {
        return materialRepository.countByCategoryId(categoryId)
                + productRepository.countByCategoryId(categoryId);
    }

    private Category findById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_CATEGORY_NOT_FOUND));
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String upperOrNull(String value) {
        String trimmed = trimOrNull(value);
        return trimmed != null ? trimmed.toUpperCase() : null;
    }
}
