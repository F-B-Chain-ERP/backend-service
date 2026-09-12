package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ComboMapper;
import com.erp.backend_service.repository.ComboItemRepository;
import com.erp.backend_service.repository.ProductRepository;
import com.erp.backend_service.repository.ProductVariantRepository;
import com.erp.backend_service.service.ComboService;
import com.erp.core.domain.ComboItem;
import com.erp.core.domain.Product;
import com.erp.core.domain.ProductVariant;
import com.erp.core.dto.request.menu.BulkSyncComboItemsRequest;
import com.erp.core.dto.response.menu.ComboDetailResponse;
import com.erp.core.dto.response.menu.ComboItemResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Triển khai {@link ComboService}: quản lý thành phần Combo với logic bulk sync
 * (soft delete items không còn trong request, reactivate items INACTIVE, validate variant + product ACTIVE).
 */
@Service
public class ComboServiceImpl implements ComboService {

    private static final Logger log = LoggerFactory.getLogger(ComboServiceImpl.class);

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ComboItemRepository comboItemRepository;
    private final ComboMapper comboMapper;

    public ComboServiceImpl(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            ComboItemRepository comboItemRepository,
            ComboMapper comboMapper
    ) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.comboItemRepository = comboItemRepository;
        this.comboMapper = comboMapper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ComboDetailResponse getDetail(UUID comboId) {
        log.info("Lấy chi tiết combo: {}", comboId);
        Product combo = productRepository.findById(comboId)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND));
        if (!combo.isCombo()) {
            throw new BaseException(ErrorCode.MENU_400_NOT_COMBO_PRODUCT);
        }

        List<ComboItem> items = comboItemRepository
                .findByComboProductIdAndStatusOrderByCreatedAtAsc(comboId, "ACTIVE");

        Map<UUID, ProductVariant> variantMap = loadVariants(items);
        Map<UUID, Product> productMap = loadProducts(variantMap);

        List<ComboItemResponse> itemResponses = items.stream()
                .map(ci -> {
                    ProductVariant v = variantMap.get(ci.getVariantId());
                    Product p = v != null ? productMap.get(v.getProductId()) : null;
                    return comboMapper.toComboItemResponse(ci, v, p);
                })
                .toList();

        return comboMapper.toComboDetailResponse(combo, itemResponses);
    }

    /** {@inheritDoc} — Atomic: validate toàn bộ → soft delete → upsert → flush. */
    @Override
    @Transactional
    public ComboDetailResponse syncItems(UUID comboId, BulkSyncComboItemsRequest request) {
        log.info("Đồng bộ thành phần combo: {}", comboId);

        Product combo = productRepository.findById(comboId)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND));
        if (!combo.isCombo()) {
            throw new BaseException(ErrorCode.MENU_400_NOT_COMBO_PRODUCT);
        }

        List<ComboItem> currentItems = comboItemRepository.findByComboProductId(comboId);
        Map<UUID, ComboItem> variantToItem = currentItems.stream()
                .collect(Collectors.toMap(ComboItem::getVariantId, it -> it, (a, b) -> a));

        // Validate TOÀN BỘ incoming entries
        Set<UUID> seenVariants = new HashSet<>();
        for (BulkSyncComboItemsRequest.SyncComboItemEntry entry : request.items()) {
            if (!seenVariants.add(entry.variantId())) {
                throw new BaseException(ErrorCode.MENU_409_COMBO_DUPLICATE_VARIANT,
                        "Biến thể ID " + entry.variantId() + " bị trùng lặp.");
            }

            ProductVariant variant = variantRepository.findById(entry.variantId())
                    .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_VARIANT_NOT_FOUND,
                            "Biến thể ID " + entry.variantId() + " không tồn tại."));

            if (!"ACTIVE".equalsIgnoreCase(variant.getStatus())) {
                throw new BaseException(ErrorCode.MENU_404_VARIANT_NOT_FOUND,
                        "Biến thể '" + variant.getVariantCode() + "' đang ngừng hoạt động.");
            }

            Product variantProduct = productRepository.findById(variant.getProductId())
                    .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND,
                            "Sản phẩm gốc của biến thể '" + variant.getVariantCode() + "' không tồn tại."));
            if (!"ACTIVE".equalsIgnoreCase(variantProduct.getStatus())) {
                throw new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND,
                        "Sản phẩm gốc '" + variantProduct.getCode() + "' của biến thể '"
                                + variant.getVariantCode() + "' đang ngừng hoạt động.");
            }

            if (entry.quantity() == null || entry.quantity() < 1) {
                throw new BaseException(ErrorCode.MENU_400_COMBO_INVALID_QUANTITY);
            }
        }

        // Soft-delete items không còn trong request
        for (ComboItem existing : currentItems) {
            if ("ACTIVE".equalsIgnoreCase(existing.getStatus()) && !seenVariants.contains(existing.getVariantId())) {
                existing.setStatus("INACTIVE");
                comboItemRepository.save(existing);
            }
        }

        // Upsert items từ request
        for (BulkSyncComboItemsRequest.SyncComboItemEntry entry : request.items()) {
            boolean isSub = Boolean.TRUE.equals(entry.isSubstitutable());

            if (variantToItem.containsKey(entry.variantId())) {
                // Reactivate INACTIVE row hoặc update
                ComboItem existing = variantToItem.get(entry.variantId());
                existing.setQuantity(entry.quantity());
                existing.setSubstitutable(isSub);
                existing.setStatus("ACTIVE");
                comboItemRepository.save(existing);
            } else {
                ComboItem newItem = new ComboItem();
                newItem.setComboProductId(comboId);
                newItem.setVariantId(entry.variantId());
                newItem.setQuantity(entry.quantity());
                newItem.setSubstitutable(isSub);
                newItem.setStatus("ACTIVE");
                comboItemRepository.save(newItem);
            }
        }

        comboItemRepository.flush();
        return getDetail(comboId);
    }

    private Map<UUID, ProductVariant> loadVariants(List<ComboItem> items) {
        Set<UUID> ids = items.stream().map(ComboItem::getVariantId).collect(Collectors.toSet());
        return variantRepository.findAllById(ids).stream()
                .filter(v -> v.getId() != null)
                .collect(Collectors.toMap(ProductVariant::getId, v -> v, (a, b) -> a));
    }

    private Map<UUID, Product> loadProducts(Map<UUID, ProductVariant> variantMap) {
        Set<UUID> productIds = variantMap.values().stream()
                .map(ProductVariant::getProductId).collect(Collectors.toSet());
        return productRepository.findAllById(productIds).stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
    }
}
