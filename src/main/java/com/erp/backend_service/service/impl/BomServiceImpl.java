package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.BomMapper;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.ProductRecipeItemRepository;
import com.erp.backend_service.repository.ProductVariantRepository;
import com.erp.backend_service.repository.UnitRepository;
import com.erp.backend_service.service.BomService;
import com.erp.core.domain.Material;
import com.erp.core.domain.ProductRecipeItem;
import com.erp.core.domain.ProductVariant;
import com.erp.core.domain.Unit;
import com.erp.core.dto.request.menu.CreateBomItemRequest;
import com.erp.core.dto.request.menu.UpdateBomItemRequest;
import com.erp.core.dto.request.menu.UpdateBomRequest;
import com.erp.core.dto.response.menu.BomItemResponse;
import com.erp.core.dto.response.menu.BomResponse;
import com.erp.core.dto.response.menu.UpdateBomResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai {@link BomService}: quản lý BOM (định mức nguyên vật liệu pha chế)
 * theo {@code ProductVariant}. Validate toàn bộ nghiệp vụ trong Service layer,
 * không dùng Jakarta Bean Validation cho business rule.
 */
@Service
public class BomServiceImpl implements BomService {

    private static final Logger log = LoggerFactory.getLogger(BomServiceImpl.class);

    private static final int MAX_QUANTITY_SCALE = 3;
    private static final int MAX_WASTAGE_SCALE = 2;

    private final ProductRecipeItemRepository recipeRepository;
    private final ProductVariantRepository variantRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final BomMapper bomMapper;

    public BomServiceImpl(ProductRecipeItemRepository recipeRepository,
                          ProductVariantRepository variantRepository,
                          MaterialRepository materialRepository,
                          UnitRepository unitRepository,
                          BomMapper bomMapper) {
        this.recipeRepository = recipeRepository;
        this.variantRepository = variantRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.bomMapper = bomMapper;
    }

    // ─────────────────────────────────────────────
    //  GET BOM
    // ─────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public BomResponse getBom(UUID variantId) {
        ProductVariant variant = findByIdVariant(variantId);
        List<ProductRecipeItem> items = recipeRepository.findByVariantIdOrderByCreatedAtAsc(variantId);

        List<BomItemResponse> itemResponses = resolveItemResponses(items);
        return bomMapper.toBomResponse(variant, itemResponses);
    }

    // ─────────────────────────────────────────────
    //  CREATE ITEM
    // ─────────────────────────────────────────────

    @Override
    @Transactional
    public BomItemResponse createItem(UUID variantId, CreateBomItemRequest request) {
        // 1. Validate null fields
        if (request == null) {
            throw new BaseException(ErrorCode.MENU_400_BOM_INVALID_REQUEST);
        }
        validateCreateItemRequest(request);

        // 2. Variant exists?
        ProductVariant variant = findByIdVariant(variantId);

        // 3. Material exists + ACTIVE?
        Material material = findByIdMaterial(request.materialId());
        if (!"ACTIVE".equalsIgnoreCase(material.getStatus())) {
            throw new BaseException(ErrorCode.MENU_400_BOM_MATERIAL_INACTIVE);
        }

        // 4. Unit exists?
        Unit unit = findByIdUnit(request.unitId());

        // 5. unitId == material.baseUnitId?
        if (!material.getBaseUnitId().equals(request.unitId())) {
            throw new BaseException(ErrorCode.MENU_400_BOM_UNIT_MISMATCH);
        }

        // 6. Validate quantity + wastage
        validateQuantity(request.quantity());
        validateWastage(request.wastagePercent());

        // 7. Duplicate?
        if (recipeRepository.existsByVariantIdAndMaterialId(variantId, request.materialId())) {
            throw new BaseException(ErrorCode.MENU_409_BOM_DUPLICATE_MATERIAL);
        }

        // 8. Save
        ProductRecipeItem entity = new ProductRecipeItem();
        entity.setVariantId(variantId);
        entity.setMaterialId(request.materialId());
        entity.setQuantity(request.quantity());
        entity.setUnitId(request.unitId());
        entity.setWastagePercent(request.wastagePercent());
        entity.setStatus("ACTIVE");

        try {
            ProductRecipeItem saved = recipeRepository.save(entity);
            return bomMapper.toItemResponse(saved, material.getName(), unit.getCode());
        } catch (DataIntegrityViolationException ex) {
            throw new BaseException(ErrorCode.MENU_409_BOM_DUPLICATE_MATERIAL);
        }
    }

    // ─────────────────────────────────────────────
    //  UPDATE ITEM
    // ─────────────────────────────────────────────

    @Override
    @Transactional
    public BomItemResponse updateItem(UUID itemId, UpdateBomItemRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.MENU_400_BOM_INVALID_REQUEST);
        }

        ProductRecipeItem item = findByIdItem(itemId);

        // Validate quantity + wastage
        validateQuantity(request.quantity());
        validateWastage(request.wastagePercent());

        item.setQuantity(request.quantity());
        item.setWastagePercent(request.wastagePercent());

        ProductRecipeItem saved = recipeRepository.save(item);

        // Resolve names for response
        Material material = materialRepository.findById(item.getMaterialId()).orElse(null);
        Unit unit = unitRepository.findById(item.getUnitId()).orElse(null);
        return bomMapper.toItemResponse(
                saved,
                material != null ? material.getName() : null,
                unit != null ? unit.getCode() : null
        );
    }

    // ─────────────────────────────────────────────
    //  DELETE ITEM
    // ─────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteItem(UUID itemId) {
        ProductRecipeItem item = findByIdItem(itemId);
        recipeRepository.delete(item);
    }

    // ─────────────────────────────────────────────
    //  BULK REPLACE
    // ─────────────────────────────────────────────

    @Override
    @Transactional
    public UpdateBomResponse bulkReplace(UUID variantId, UpdateBomRequest request) {
        // 1. Validate request not null
        if (request == null || request.items() == null) {
            throw new BaseException(ErrorCode.MENU_400_BOM_INVALID_REQUEST);
        }

        // 2. Variant exists?
        ProductVariant variant = findByIdVariant(variantId);

        // 3. Load existing BOM TRƯỚC validation
        List<ProductRecipeItem> existingItems =
                recipeRepository.findByVariantIdOrderByCreatedAtAsc(variantId);
        Map<UUID, ProductRecipeItem> existingByMaterialId = existingItems.stream()
                .collect(Collectors.toMap(ProductRecipeItem::getMaterialId, Function.identity(), (a, b) -> a));

        // 4. Validate TOÀN BỘ request items
        List<CreateBomItemRequest> items = request.items();
        Set<UUID> requestMaterialIds = new HashSet<>();

        for (CreateBomItemRequest item : items) {
            if (item == null) {
                throw new BaseException(ErrorCode.MENU_400_BOM_INVALID_REQUEST);
            }
            validateBulkItemRequest(item);

            UUID materialId = item.materialId();

            // Duplicate materialId trong request?
            if (!requestMaterialIds.add(materialId)) {
                throw new BaseException(ErrorCode.MENU_409_BOM_DUPLICATE_MATERIAL);
            }

            // Material exists?
            Material material = findByIdMaterial(materialId);

            // Material INACTIVE + chưa có trong BOM → reject
            if (!"ACTIVE".equalsIgnoreCase(material.getStatus())
                    && !existingByMaterialId.containsKey(materialId)) {
                throw new BaseException(ErrorCode.MENU_400_BOM_MATERIAL_INACTIVE);
            }

            // Unit exists?
            Unit unit = findByIdUnit(item.unitId());

            // unitId == material.baseUnitId?
            if (!material.getBaseUnitId().equals(item.unitId())) {
                throw new BaseException(ErrorCode.MENU_400_BOM_UNIT_MISMATCH);
            }

            // Validate quantity + wastage
            validateQuantity(item.quantity());
            validateWastage(item.wastagePercent());
        }

        // 5. Validate thành công → mutate DB
        List<ProductRecipeItem> toSave = new ArrayList<>();
        List<ProductRecipeItem> toDelete = new ArrayList<>();

        // A + B: Duyệt request items → update hoặc insert
        for (CreateBomItemRequest item : items) {
            UUID materialId = item.materialId();
            ProductRecipeItem existing = existingByMaterialId.get(materialId);

            if (existing != null) {
                // A: Material đã có → update
                existing.setQuantity(item.quantity());
                existing.setUnitId(item.unitId());
                existing.setWastagePercent(item.wastagePercent());
                toSave.add(existing);
            } else {
                // B: Material chưa có → insert mới
                ProductRecipeItem newEntity = new ProductRecipeItem();
                newEntity.setVariantId(variantId);
                newEntity.setMaterialId(materialId);
                newEntity.setQuantity(item.quantity());
                newEntity.setUnitId(item.unitId());
                newEntity.setWastagePercent(item.wastagePercent());
                newEntity.setStatus("ACTIVE");
                toSave.add(newEntity);
            }
        }

        // C: Material cũ không còn trong request → hard delete
        for (ProductRecipeItem existing : existingItems) {
            if (!requestMaterialIds.contains(existing.getMaterialId())) {
                toDelete.add(existing);
            }
        }

        // Execute saves + deletes
        recipeRepository.saveAll(toSave);
        if (!toDelete.isEmpty()) {
            recipeRepository.deleteAll(toDelete);
        }

        return new UpdateBomResponse(variantId.toString(), toSave.size());
    }

    // ─────────────────────────────────────────────
    //  PRIVATE HELPERS
    // ─────────────────────────────────────────────

    private ProductVariant findByIdVariant(UUID id) {
        return variantRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_VARIANT_NOT_FOUND));
    }

    private ProductRecipeItem findByIdItem(UUID id) {
        return recipeRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_BOM_ITEM_NOT_FOUND));
    }

    private Material findByIdMaterial(UUID id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MATERIAL_NOT_FOUND));
    }

    private Unit findByIdUnit(UUID id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_UNIT_NOT_FOUND));
    }

    /**
     * Validate request tạo item: kiểm tra null và throw MENU_400_BOM_INVALID_REQUEST.
     */
    private void validateCreateItemRequest(CreateBomItemRequest request) {
        if (request.materialId() == null
                || request.quantity() == null
                || request.unitId() == null
                || request.wastagePercent() == null) {
            throw new BaseException(ErrorCode.MENU_400_BOM_INVALID_REQUEST);
        }
    }

    /**
     * Validate request bulk item: kiểm tra null và throw MENU_400_BOM_INVALID_REQUEST.
     */
    private void validateBulkItemRequest(CreateBomItemRequest item) {
        if (item.materialId() == null
                || item.quantity() == null
                || item.unitId() == null
                || item.wastagePercent() == null) {
            throw new BaseException(ErrorCode.MENU_400_BOM_INVALID_REQUEST);
        }
    }

    /**
     * Validate quantity: phải > 0, scale <= 3, precision <= 12.
     */
    private void validateQuantity(BigDecimal quantity) {
        if (quantity == null
                || quantity.compareTo(BigDecimal.ZERO) <= 0
                || quantity.scale() > MAX_QUANTITY_SCALE) {
            throw new BaseException(ErrorCode.MENU_400_BOM_INVALID_QUANTITY);
        }
    }

    /**
     * Validate wastagePercent: phải >= 0, <= 100, scale <= 2.
     */
    private void validateWastage(BigDecimal wastage) {
        if (wastage == null
                || wastage.compareTo(BigDecimal.ZERO) < 0
                || wastage.compareTo(new BigDecimal("100")) > 0
                || wastage.scale() > MAX_WASTAGE_SCALE) {
            throw new BaseException(ErrorCode.MENU_400_BOM_INVALID_WASTAGE);
        }
    }

    /**
     * Resolve tên material và unitCode cho danh sách dòng BOM, tránh N+1.
     * Follow pattern {@code resolveNames} trong SupplierMaterialServiceImpl.
     */
    private List<BomItemResponse> resolveItemResponses(List<ProductRecipeItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }

        List<UUID> materialIds = items.stream()
                .map(ProductRecipeItem::getMaterialId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<UUID> unitIds = items.stream()
                .map(ProductRecipeItem::getUnitId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, Material> materialMap = materialRepository.findAllById(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity(), (a, b) -> a));
        Map<UUID, Unit> unitMap = unitRepository.findAllById(unitIds).stream()
                .collect(Collectors.toMap(Unit::getId, Function.identity(), (a, b) -> a));

        return items.stream()
                .map(e -> {
                    Material mat = materialMap.get(e.getMaterialId());
                    Unit u = unitMap.get(e.getUnitId());
                    return bomMapper.toItemResponse(
                            e,
                            mat != null ? mat.getName() : null,
                            u != null ? u.getCode() : null
                    );
                })
                .toList();
    }
}
