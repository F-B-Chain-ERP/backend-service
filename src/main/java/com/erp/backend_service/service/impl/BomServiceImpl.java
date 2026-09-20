package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.BomMapper;
import com.erp.backend_service.repository.*;
import com.erp.backend_service.service.BomService;
import com.erp.backend_service.service.UnitConversionService;
import com.erp.core.domain.*;
import com.erp.core.dto.request.menu.AddBomItemRequest;
import com.erp.core.dto.request.menu.BulkSyncBomRequest;
import com.erp.core.dto.request.menu.UpdateBomItemRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.BomResponse;
import com.erp.core.dto.response.menu.ProductBomOverviewResponse;
import com.erp.core.dto.response.menu.ProductRecipeItemResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hiện thực nghiệp vụ quản lý công thức định lượng (BOM).
 */
@Service
public class BomServiceImpl implements BomService {

    private static final Logger log = LoggerFactory.getLogger(BomServiceImpl.class);

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRecipeItemRepository productRecipeItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final UnitConversionService unitConversionService;
    private final BomMapper bomMapper;

    public BomServiceImpl(
            ProductRecipeItemRepository productRecipeItemRepository,
            ProductVariantRepository productVariantRepository,
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            UnitConversionService unitConversionService,
            BomMapper bomMapper
    ) {
        this.productRecipeItemRepository = productRecipeItemRepository;
        this.productVariantRepository = productVariantRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.unitConversionService = unitConversionService;
        this.bomMapper = bomMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public BomResponse getBomByVariantId(UUID variantId) {
        log.info("Lấy công thức định lượng (BOM) cho biến thể: {}", variantId);
        ProductVariant variant = ensureVariantExists(variantId);
        Product product = productRepository.findById(variant.getProductId()).orElse(null);
        String productName = product != null ? product.getName() : "";

        List<ProductRecipeItem> items = productRecipeItemRepository
                .findByVariantIdAndStatusOrderByCreatedAtAsc(variantId, "ACTIVE");

        Map<UUID, Material> materialMap = getMaterialMap(items);
        Map<UUID, Unit> unitMap = getUnitMap(items);

        List<ProductRecipeItemResponse> itemResponses = items.stream().map(item -> {
            Material m = materialMap.get(item.getMaterialId());
            Unit u = unitMap.get(item.getUnitId());
            return bomMapper.toItemResponse(
                    item,
                    m != null ? m.getName() : "",
                    m != null ? m.getCode() : "",
                    u != null ? u.getCode() : ""
            );
        }).toList();

        return bomMapper.toBomResponse(variant, productName, itemResponses);
    }

    @Override
    @Transactional
    public ProductRecipeItemResponse addItem(UUID variantId, AddBomItemRequest request) {
        log.info("Thêm nguyên vật liệu vào BOM của biến thể: {}, materialId: {}", variantId, request.materialId());
        ensureVariantExists(variantId);

        Material material = materialRepository.findById(request.materialId())
                .orElseThrow(() -> new BaseException(ErrorCode.MATERIAL_NOT_FOUND));

        if (!"ACTIVE".equalsIgnoreCase(material.getStatus())) {
            throw new BaseException(ErrorCode.MENU_400_BOM_MATERIAL_INACTIVE);
        }

        Unit unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_UNIT_NOT_FOUND));

        if (!"ACTIVE".equalsIgnoreCase(unit.getStatus())) {
            throw new BaseException(ErrorCode.MENU_404_UNIT_NOT_FOUND);
        }

        // Đơn vị dòng BOM: gốc, pack của NVL, hoặc cùng nhóm có dòng quy đổi.
        unitConversionService.validateBomUnit(material, unit);

        validateQuantity(request.quantity());
        validateWastage(request.wastagePercent());

        BigDecimal wastage = request.wastagePercent() != null ? request.wastagePercent() : BigDecimal.ZERO;

        // Kiểm tra nếu đã tồn tại record cho cặp (variantId, materialId)
        Optional<ProductRecipeItem> existingOpt = productRecipeItemRepository
                .findByVariantIdAndMaterialId(variantId, request.materialId());

        ProductRecipeItem saved;
        if (existingOpt.isPresent()) {
            ProductRecipeItem existing = existingOpt.get();
            if ("ACTIVE".equalsIgnoreCase(existing.getStatus())) {
                throw new BaseException(ErrorCode.MENU_409_BOM_MATERIAL_DUPLICATED);
            }
            // Tái kích hoạt dòng đã bị xóa mềm trước đó
            existing.setQuantity(request.quantity());
            existing.setUnitId(request.unitId());
            existing.setWastagePercent(wastage);
            existing.setStatus("ACTIVE");
            saved = productRecipeItemRepository.save(existing);
        } else {
            ProductRecipeItem newItem = new ProductRecipeItem();
            newItem.setVariantId(variantId);
            newItem.setMaterialId(request.materialId());
            newItem.setQuantity(request.quantity());
            newItem.setUnitId(request.unitId());
            newItem.setWastagePercent(wastage);
            newItem.setStatus("ACTIVE");
            saved = productRecipeItemRepository.save(newItem);
        }

        return bomMapper.toItemResponse(saved, material.getName(), material.getCode(), unit.getCode());
    }

    @Override
    @Transactional
    public ProductRecipeItemResponse updateItem(UUID variantId, UUID itemId, UpdateBomItemRequest request) {
        log.info("Cập nhật dòng BOM item: {} của biến thể: {}", itemId, variantId);
        ensureVariantExists(variantId);

        ProductRecipeItem item = productRecipeItemRepository.findByIdAndVariantId(itemId, variantId)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_BOM_ITEM_NOT_FOUND));

        Material material = materialRepository.findById(request.materialId())
                .orElseThrow(() -> new BaseException(ErrorCode.MATERIAL_NOT_FOUND));

        if (!"ACTIVE".equalsIgnoreCase(material.getStatus())) {
            throw new BaseException(ErrorCode.MENU_400_BOM_MATERIAL_INACTIVE);
        }

        Unit unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_UNIT_NOT_FOUND));

        if (!"ACTIVE".equalsIgnoreCase(unit.getStatus())) {
            throw new BaseException(ErrorCode.MENU_404_UNIT_NOT_FOUND);
        }

        // Đơn vị dòng BOM: gốc, pack của NVL, hoặc cùng nhóm có dòng quy đổi.
        unitConversionService.validateBomUnit(material, unit);

        validateQuantity(request.quantity());
        validateWastage(request.wastagePercent());

        // Nếu thay đổi materialId, kiểm tra tính duy nhất
        if (!item.getMaterialId().equals(request.materialId())) {
            boolean duplicated = productRecipeItemRepository
                    .existsByVariantIdAndMaterialIdAndIdNotAndStatus(variantId, request.materialId(), itemId, "ACTIVE");
            if (duplicated) {
                throw new BaseException(ErrorCode.MENU_409_BOM_MATERIAL_DUPLICATED);
            }
            // Nếu có 1 record INACTIVE cũ với materialId đó, xóa bỏ để tránh vi phạm unique constraint
            productRecipeItemRepository.findByVariantIdAndMaterialId(variantId, request.materialId())
                    .ifPresent(inactiveItem -> {
                        if (!inactiveItem.getId().equals(itemId) && !"ACTIVE".equalsIgnoreCase(inactiveItem.getStatus())) {
                            productRecipeItemRepository.delete(inactiveItem);
                            productRecipeItemRepository.flush();
                        }
                    });
            item.setMaterialId(request.materialId());
        }

        BigDecimal wastage = request.wastagePercent() != null ? request.wastagePercent() : BigDecimal.ZERO;
        item.setQuantity(request.quantity());
        item.setUnitId(request.unitId());
        item.setWastagePercent(wastage);
        item.setStatus("ACTIVE");

        ProductRecipeItem saved = productRecipeItemRepository.save(item);
        return bomMapper.toItemResponse(saved, material.getName(), material.getCode(), unit.getCode());
    }

    @Override
    @Transactional
    public void removeItem(UUID variantId, UUID itemId) {
        log.info("Gỡ dòng BOM item: {} của biến thể: {} (xóa mềm)", itemId, variantId);
        ensureVariantExists(variantId);

        ProductRecipeItem item = productRecipeItemRepository.findByIdAndVariantId(itemId, variantId)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_BOM_ITEM_NOT_FOUND));

        // Xóa mềm theo yêu cầu người dùng
        item.setStatus("INACTIVE");
        productRecipeItemRepository.save(item);
    }

    @Override
    @Transactional
    public BomResponse syncBom(UUID variantId, BulkSyncBomRequest request) {
        log.info("Đồng bộ toàn bộ công thức định lượng (BOM) cho biến thể: {}", variantId);
        ProductVariant variant = ensureVariantExists(variantId);

        List<BulkSyncBomRequest.SyncBomItemEntry> incomingEntries = request.items() != null
                ? request.items()
                : Collections.emptyList();

        // 1. Load existing items TRƯỚC validation
        List<ProductRecipeItem> currentItems = productRecipeItemRepository.findByVariantId(variantId);
        Map<UUID, ProductRecipeItem> materialToItemMap = currentItems.stream()
                .collect(Collectors.toMap(ProductRecipeItem::getMaterialId, it -> it, (a, b) -> a));

        // 2. Validate TOÀN BỘ request items
        Set<UUID> seenMaterials = new HashSet<>();
        for (BulkSyncBomRequest.SyncBomItemEntry entry : incomingEntries) {
            if (!seenMaterials.add(entry.materialId())) {
                throw new BaseException(ErrorCode.MENU_409_BOM_MATERIAL_DUPLICATED,
                        "Nguyên vật liệu ID " + entry.materialId() + " bị trùng lặp trong danh sách gửi lên.");
            }

            Material material = materialRepository.findById(entry.materialId())
                    .orElseThrow(() -> new BaseException(ErrorCode.MATERIAL_NOT_FOUND));

            if (!"ACTIVE".equalsIgnoreCase(material.getStatus())
                    && !materialToItemMap.containsKey(entry.materialId())) {
                throw new BaseException(ErrorCode.MENU_400_BOM_MATERIAL_INACTIVE);
            }

            Unit unit = unitRepository.findById(entry.unitId())
                    .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_UNIT_NOT_FOUND));

            if (!"ACTIVE".equalsIgnoreCase(unit.getStatus())) {
                throw new BaseException(ErrorCode.MENU_404_UNIT_NOT_FOUND);
            }

            // Đơn vị dòng BOM: gốc, pack của NVL, hoặc cùng nhóm có dòng quy đổi.
            unitConversionService.validateBomUnit(material, unit);

            validateQuantity(entry.quantity());
            validateWastage(entry.wastagePercent());
        }

        // 3. Validate thành công → mutate DB

        // Xóa mềm các item đang ACTIVE nhưng không còn trong incoming request
        for (ProductRecipeItem existing : currentItems) {
            if ("ACTIVE".equalsIgnoreCase(existing.getStatus()) && !seenMaterials.contains(existing.getMaterialId())) {
                existing.setStatus("INACTIVE");
                productRecipeItemRepository.save(existing);
            }
        }

        // Thêm mới hoặc cập nhật / tái kích hoạt
        for (BulkSyncBomRequest.SyncBomItemEntry entry : incomingEntries) {
            BigDecimal wastage = entry.wastagePercent() != null ? entry.wastagePercent() : BigDecimal.ZERO;

            if (materialToItemMap.containsKey(entry.materialId())) {
                ProductRecipeItem existing = materialToItemMap.get(entry.materialId());
                existing.setQuantity(entry.quantity());
                existing.setUnitId(entry.unitId());
                existing.setWastagePercent(wastage);
                existing.setStatus("ACTIVE");
                productRecipeItemRepository.save(existing);
            } else {
                ProductRecipeItem newItem = new ProductRecipeItem();
                newItem.setVariantId(variantId);
                newItem.setMaterialId(entry.materialId());
                newItem.setQuantity(entry.quantity());
                newItem.setUnitId(entry.unitId());
                newItem.setWastagePercent(wastage);
                newItem.setStatus("ACTIVE");
                productRecipeItemRepository.save(newItem);
            }
        }

        productRecipeItemRepository.flush();
        return getBomByVariantId(variantId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductBomOverviewResponse> getBomOverview(
            int page,
            int size,
            String search,
            UUID categoryId,
            String bomStatus) {
        log.info("Lấy danh sách tổng quan BOM, page: {}, search: {}, categoryId: {}, bomStatus: {}",
                page, search, categoryId, bomStatus);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        // 1 query: phân trang biến thể + filt theo search/category/bomStatus
        Page<ProductVariant> variantPage = productVariantRepository.searchForBomOverview(
                StringUtils.hasText(search) ? search.trim() : null,
                categoryId,
                StringUtils.hasText(bomStatus) ? bomStatus.trim().toUpperCase() : null,
                pageable);

        List<ProductVariant> variants = variantPage.getContent();
        if (variants.isEmpty()) {
            return new PageResponse<>(
                    variantPage.getNumber(),
                    variantPage.getSize(),
                    variantPage.getTotalElements(),
                    variantPage.getTotalPages(),
                    List.of());
        }

        Set<UUID> variantIds = variants.stream().map(ProductVariant::getId).collect(Collectors.toSet());
        Set<UUID> productIds = variants.stream().map(ProductVariant::getProductId).collect(Collectors.toSet());

        // 1 query: load sản phẩm (tên, mã, basePrice, categoryId)
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        // 1 query bulk: đếm số NVL ACTIVE theo nhóm biến thể
        Map<UUID, Integer> itemCountMap = countActiveItemsByVariant(variantIds);

        // 1 query: load tên danh mục
        Set<UUID> categoryIds = productMap.values().stream()
                .map(Product::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Category> categoryMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryRepository.findAllById(categoryIds).stream()
                        .filter(c -> c.getId() != null)
                        .collect(Collectors.toMap(Category::getId, c -> c, (a, b) -> a));

        // Map kết quả
        List<ProductBomOverviewResponse> content = new ArrayList<>(variants.size());
        for (ProductVariant v : variants) {
            Product p = productMap.get(v.getProductId());
            if (p == null) {
                continue;
            }
            String categoryName = p.getCategoryId() != null && categoryMap.containsKey(p.getCategoryId())
                    ? categoryMap.get(p.getCategoryId()).getName()
                    : "";
            int itemCount = itemCountMap.getOrDefault(v.getId(), 0);
            content.add(bomMapper.toOverviewResponse(v, p, categoryName, itemCount));
        }

        return new PageResponse<>(
                variantPage.getNumber(),
                variantPage.getSize(),
                variantPage.getTotalElements(),
                variantPage.getTotalPages(),
                content);
    }

    private Map<UUID, Integer> countActiveItemsByVariant(Set<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Integer> countMap = new HashMap<>();
        for (Object[] row : productRecipeItemRepository.countByVariantIdsAndStatus(variantIds, "ACTIVE")) {
            if (row.length >= 2
                    && row[0] instanceof UUID variantId
                    && row[1] instanceof Number count) {
                countMap.put(variantId, count.intValue());
            }
        }
        return countMap;
    }

    private ProductVariant ensureVariantExists(UUID variantId) {
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_VARIANT_NOT_FOUND));
    }

    private Map<UUID, Material> getMaterialMap(List<ProductRecipeItem> items) {
        Set<UUID> materialIds = items.stream().map(ProductRecipeItem::getMaterialId).collect(Collectors.toSet());
        return materialRepository.findAllById(materialIds).stream()
                .filter(m -> m.getId() != null)
                .collect(Collectors.toMap(Material::getId, m -> m, (a, b) -> a));
    }

    private Map<UUID, Unit> getUnitMap(List<ProductRecipeItem> items) {
        Set<UUID> unitIds = items.stream().map(ProductRecipeItem::getUnitId).collect(Collectors.toSet());
        return unitRepository.findAllById(unitIds).stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(Unit::getId, u -> u, (a, b) -> a));
    }

    private void validateQuantity(BigDecimal quantity) {
        if (quantity == null
                || quantity.compareTo(BigDecimal.ZERO) <= 0
                || quantity.scale() > 3) {
            throw new BaseException(ErrorCode.MENU_400_BOM_INVALID_QUANTITY);
        }
    }

    private void validateWastage(BigDecimal wastage) {
        if (wastage == null
                || wastage.compareTo(BigDecimal.ZERO) <= 0
                || wastage.compareTo(new BigDecimal("100")) > 0
                || wastage.scale() > 2) {
            throw new BaseException(ErrorCode.MENU_400_BOM_INVALID_WASTAGE);
        }
    }
}
