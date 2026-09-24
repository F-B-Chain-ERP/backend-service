package com.erp.backend_service.service.pos;

import com.erp.backend_service.repository.ComboItemRepository;
import com.erp.backend_service.repository.ProductRepository;
import com.erp.backend_service.repository.ProductVariantRepository;
import com.erp.core.domain.ComboItem;
import com.erp.core.domain.Product;
import com.erp.core.domain.ProductVariant;
import com.erp.core.dto.response.menu.ComboItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Đọc thành phần combo cho kênh bán hàng (POS/web) với query batch, không N+1:
 *   combo_item (theo comboProductId) -> product_variant -> product.
 * Rule khớp PosComboService: chỉ lấy combo_item ACTIVE; bỏ qua variant không hợp lệ
 * (thiếu hoặc không ACTIVE) và product đã DELETED.
 */
@Service
public class ComboSalesService {

    private static final String ACTIVE = "ACTIVE";

    private final ComboItemRepository comboItemRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;

    public ComboSalesService(ComboItemRepository comboItemRepository,
                             ProductVariantRepository variantRepository,
                             ProductRepository productRepository) {
        this.comboItemRepository = comboItemRepository;
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<ComboItemResponse>> comboItemsByProduct(Collection<UUID> comboProductIds) {
        if (comboProductIds == null || comboProductIds.isEmpty()) {
            return Map.of();
        }
        List<ComboItem> items = comboItemRepository.findByComboProductIdInAndStatus(comboProductIds, ACTIVE);
        if (items.isEmpty()) {
            return Map.of();
        }

        Set<UUID> variantIds = items.stream()
                .map(ComboItem::getVariantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, ProductVariant> variantMap = variantIds.isEmpty() ? Map.of()
                : variantRepository.findAllById(variantIds).stream()
                        .filter(v -> ACTIVE.equalsIgnoreCase(v.getStatus()))
                        .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        Set<UUID> memberProductIds = variantMap.values().stream()
                .map(ProductVariant::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Product> memberMap = memberProductIds.isEmpty() ? Map.of()
                : productRepository.findAllById(memberProductIds).stream()
                        .filter(p -> !"DELETED".equalsIgnoreCase(p.getStatus()))
                        .collect(Collectors.toMap(Product::getId, p -> p));

        Map<UUID, List<ComboItemResponse>> result = new HashMap<>();
        for (UUID comboId : comboProductIds) {
            List<ComboItemResponse> list = items.stream()
                    .filter(i -> comboId.equals(i.getComboProductId()))
                    .sorted(Comparator.comparing(ComboItem::getCreatedAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(i -> i.getId() != null ? i.getId().toString() : ""))
                    .map(i -> toResponse(i, variantMap, memberMap))
                    .filter(Objects::nonNull)
                    .toList();
            result.put(comboId, list);
        }
        return result;
    }

    private ComboItemResponse toResponse(ComboItem item, Map<UUID, ProductVariant> variantMap,
                                         Map<UUID, Product> memberMap) {
        ProductVariant variant = variantMap.get(item.getVariantId());
        if (variant == null) {
            return null;
        }
        Product product = memberMap.get(variant.getProductId());
        if (product == null) {
            return null;
        }
        BigDecimal unitPrice = product.getBasePrice() != null ? product.getBasePrice() : BigDecimal.ZERO;
        if (variant.getPriceDelta() != null) {
            unitPrice = unitPrice.add(variant.getPriceDelta());
        }
        return new ComboItemResponse(
                item.getId() != null ? item.getId().toString() : null,
                variant.getId().toString(),
                variant.getVariantCode(),
                variant.getVariantName(),
                variant.getSizeLabel(),
                product.getId().toString(),
                product.getCode(),
                product.getName(),
                unitPrice,
                item.getQuantity(),
                item.isSubstitutable(),
                item.getStatus(),
                unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()))
        );
    }
}