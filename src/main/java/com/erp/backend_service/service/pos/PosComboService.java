package com.erp.backend_service.service.pos;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.ComboItemRepository;
import com.erp.backend_service.repository.ProductRepository;
import com.erp.core.domain.ComboItem;
import com.erp.core.domain.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validate combo theo MENU (A4):
 * - SP isCombo=true bắt buộc có combo_item ACTIVE, trừ kho từng thành phần.
 * - SP thường: check variant trực tiếp (kể cả null -> bỏ qua, caller quyết).
 */
@Service
public class PosComboService {
    public record SaleLine(UUID productId, UUID variantId, int quantity, boolean combo) {
    }


    private final ProductRepository productRepository;
    private final ComboItemRepository comboItemRepository;
    private final PosStockService stockService;

    public PosComboService(ProductRepository productRepository,
                           ComboItemRepository comboItemRepository,
                           PosStockService stockService) {
        this.productRepository = productRepository;
        this.comboItemRepository = comboItemRepository;
        this.stockService = stockService;
    }

    @Transactional(readOnly = true)
    public void validateForSale(UUID productId, UUID variantId, int quantity, UUID branchId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_PRODUCT_NOT_FOUND));
        validateForSale(product, variantId, quantity, branchId);
    }

    public void validateForSale(Product product, UUID variantId, int quantity, UUID branchId) {
        UUID productId = product.getId();
        if (!product.isCombo()) {
            stockService.checkAvailable(branchId, variantId, quantity);
            return;
        }
        List<ComboItem> items = sortedComboItems(productId);
        if (items.isEmpty()) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Combo chưa cấu hình thành phần, không thể bán.");
        }
        for (ComboItem item : items) {
            stockService.checkAvailable(branchId, item.getVariantId(), item.getQuantity() * quantity);
        }
    }

    @Transactional
    public void reserveForSale(UUID branchId, UUID productId, UUID variantId, int quantity, UUID orderId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_PRODUCT_NOT_FOUND));
        reserveForSale(branchId, productId, variantId, quantity, orderId, product.isCombo());
    }

    public void reserveForSale(UUID branchId, UUID productId, UUID variantId, int quantity, UUID orderId,
                               boolean combo) {
        if (!combo) {
            stockService.reserve(branchId, variantId, quantity, orderId);
            return;
        }
        List<ComboItem> items = sortedComboItems(productId);
        for (ComboItem item : items) {
            stockService.reserve(branchId, item.getVariantId(), item.getQuantity() * quantity, orderId);
        }
    }

    @Transactional
    public void releaseForSale(UUID branchId, UUID productId, UUID variantId, int quantity, UUID orderId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return;
        }
        releaseForSale(branchId, productId, variantId, quantity, orderId, product.isCombo());
    }

    public void releaseForSale(UUID branchId, UUID productId, UUID variantId, int quantity, UUID orderId,
                               boolean combo) {
        if (!combo) {
            stockService.release(branchId, variantId, quantity, orderId);
            return;
        }
        List<ComboItem> items = sortedComboItems(productId);
        for (ComboItem item : items) {
            stockService.release(branchId, item.getVariantId(), item.getQuantity() * quantity, orderId);
        }
    }

    /**
     * Gom toàn bộ line (kể cả thành phần combo) theo variant rồi khóa theo UUID tăng dần.
     * Nhờ đó hai đơn giao nhau về tồn kho không thể giữ hai variant theo thứ tự ngược nhau.
     */
    public void reserveAllForSale(UUID branchId, Collection<SaleLine> lines, UUID orderId) {
        for (Map.Entry<UUID, Integer> entry : expandedQuantities(lines).entrySet()) {
            stockService.reserve(branchId, entry.getKey(), entry.getValue(), orderId);
        }
    }

    public void releaseAllForSale(UUID branchId, Collection<SaleLine> lines, UUID orderId) {
        for (Map.Entry<UUID, Integer> entry : expandedQuantities(lines).entrySet()) {
            stockService.release(branchId, entry.getKey(), entry.getValue(), orderId);
        }
    }

    private Map<UUID, Integer> expandedQuantities(Collection<SaleLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return Map.of();
        }
        Set<UUID> comboIds = lines.stream().filter(SaleLine::combo).map(SaleLine::productId)
            .collect(Collectors.toSet());
        Map<UUID, List<ComboItem>> comboItems = comboIds.isEmpty() ? Map.of()
            : comboItemRepository.findByComboProductIdInAndStatus(comboIds, "ACTIVE").stream()
                .collect(Collectors.groupingBy(ComboItem::getComboProductId));
        Map<UUID, Integer> quantities = new java.util.TreeMap<>();
        for (SaleLine line : lines) {
            if (line.quantity() <= 0) {
                continue;
            }
            if (!line.combo()) {
                if (line.variantId() != null) {
                    quantities.merge(line.variantId(), line.quantity(), Math::addExact);
                }
                continue;
            }
            for (ComboItem item : comboItems.getOrDefault(line.productId(), List.of())) {
                if (item.getVariantId() == null) {
                    continue;
                }
                quantities.merge(item.getVariantId(), Math.multiplyExact(item.getQuantity(), line.quantity()),
                    Math::addExact);
            }
        }
        return quantities;
    }

    private List<ComboItem> sortedComboItems(UUID productId) {
        List<ComboItem> items = new ArrayList<>(
            comboItemRepository.findByComboProductIdAndStatusOrderByCreatedAtAsc(productId, "ACTIVE"));
        items.sort(Comparator.comparing(item -> item.getVariantId().toString()));
        return items;
    }
}
