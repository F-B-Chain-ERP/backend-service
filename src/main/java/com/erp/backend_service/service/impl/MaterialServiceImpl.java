package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.MaterialMapper;
import com.erp.backend_service.repository.CategoryRepository;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.PurchaseOrderItemRepository;
import com.erp.backend_service.repository.UnitRepository;
import com.erp.backend_service.service.MaterialService;
import com.erp.core.domain.Category;
import com.erp.core.domain.Material;
import com.erp.core.domain.Unit;
import com.erp.core.dto.request.inv.CreateMaterialRequest;
import com.erp.core.dto.request.inv.UpdateMaterialRequest;
import com.erp.core.dto.response.Material.MaterialResponse;
import com.erp.core.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MaterialServiceImpl implements MaterialService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MaterialRepository materialRepository;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final MaterialMapper materialMapper;

    public MaterialServiceImpl(MaterialRepository materialRepository,
                               CategoryRepository categoryRepository,
                               UnitRepository unitRepository,
                               PurchaseOrderItemRepository purchaseOrderItemRepository,
                               MaterialMapper materialMapper) {
        this.materialRepository = materialRepository;
        this.categoryRepository = categoryRepository;
        this.unitRepository = unitRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.materialMapper = materialMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MaterialResponse> list(int page, int size, String search, UUID categoryId, String status, Boolean isPerishable) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending());
        Page<Material> pageResult = materialRepository.search(
                StringUtils.hasText(search) ? search.trim() : null,
                categoryId,
                StringUtils.hasText(status) ? status.trim().toUpperCase() : null,
                isPerishable,
                pageable);

        List<Material> materials = pageResult.getContent();

        Map<UUID, String> categoryNameMap = resolveCategoryNames(materials);
        Map<UUID, String> unitNameMap = resolveUnitNames(materials);

        return new PageResponse<>(
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                materials.stream()
                        .map(m -> materialMapper.toResponse(m, categoryNameMap.get(m.getCategoryId()), unitNameMap.get(m.getBaseUnitId())))
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialResponse get(UUID id) {
        Material material = findById(id);
        String categoryName = resolveCategoryName(material.getCategoryId());
        String unitName = resolveUnitName(material.getBaseUnitId());
        return materialMapper.toDetailResponse(material, categoryName, unitName);
    }

    @Override
    @Transactional
    public MaterialResponse create(CreateMaterialRequest request) {
        if (materialRepository.existsByCode(request.code())) {
            throw new BaseException(ErrorCode.INV_400_MATERIAL_CODE_EXISTED);
        }
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_CATEGORY_NOT_FOUND));
        if (!"MATERIAL".equals(category.getCategoryType()) || !"ACTIVE".equals(category.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_INVALID_MATERIAL_CATEGORY);
        }
        if (!unitRepository.existsById(request.baseUnitId())) {
            throw new BaseException(ErrorCode.INV_404_UNIT_NOT_FOUND);
        }

        Material material = materialMapper.toEntity(request);
        return materialMapper.toResponse(materialRepository.save(material));
    }

    @Override
    @Transactional
    public MaterialResponse update(UUID id, UpdateMaterialRequest request) {
        Material material = findById(id);
        if (!material.getCode().equals(request.code()) && materialRepository.existsByCode(request.code())) {
            throw new BaseException(ErrorCode.INV_400_MATERIAL_CODE_EXISTED);
        }
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_CATEGORY_NOT_FOUND));
        if (!"MATERIAL".equals(category.getCategoryType()) || !"ACTIVE".equals(category.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_INVALID_MATERIAL_CATEGORY);
        }
        if (!unitRepository.existsById(request.baseUnitId())) {
            throw new BaseException(ErrorCode.INV_404_UNIT_NOT_FOUND);
        }
        String status = request.status() != null ? request.status().trim().toUpperCase() : null;
        if (status != null && !"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }

        materialMapper.updateEntity(material, request);
        if (status != null) {
            material.setStatus(status);
        }
        return materialMapper.toResponse(materialRepository.save(material));
    }

    @Override
    @Transactional
    public MaterialResponse updateStatus(UUID id, String status) {
        Material material = findById(id);

        if (!StringUtils.hasText(status)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }

        String normalizedStatus = status.trim().toUpperCase();

        if (!"ACTIVE".equals(normalizedStatus) && !"INACTIVE".equals(normalizedStatus)) {
            throw new BaseException(ErrorCode.INV_400_MATERIAL_INVALID_STATUS);
        }

        material.setStatus(normalizedStatus);
        return materialMapper.toResponse(materialRepository.save(material));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Material material = findById(id);

        if (purchaseOrderItemRepository.existsByMaterialId(id)) {
            throw new BaseException(ErrorCode.INV_400_MATERIAL_IN_USE);
        }

        materialRepository.deleteById(id);
    }

    private Material findById(UUID id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MATERIAL_NOT_FOUND));
    }

    private String resolveCategoryName(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId).map(Category::getName).orElse(null);
    }

    private String resolveUnitName(UUID unitId) {
        if (unitId == null) {
            return null;
        }
        return unitRepository.findById(unitId).map(Unit::getName).orElse(null);
    }

    private Map<UUID, String> resolveCategoryNames(List<Material> materials) {
        List<UUID> categoryIds = materials.stream()
                .map(Material::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private Map<UUID, String> resolveUnitNames(List<Material> materials) {
        List<UUID> unitIds = materials.stream()
                .map(Material::getBaseUnitId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (unitIds.isEmpty()) {
            return Map.of();
        }
        return unitRepository.findAllById(unitIds).stream()
                .collect(Collectors.toMap(Unit::getId, Unit::getName));
    }
}
