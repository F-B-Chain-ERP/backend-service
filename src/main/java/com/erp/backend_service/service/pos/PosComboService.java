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

/**
 * Validate combo theo MENU (A4):
 * - SP isCombo=true bắt buộc có combo_item ACTIVE, trừ kho từng thành phần.
 * - SP thường: check variant trực tiếp (kể cả null -> bỏ qua, caller quyết).
 */
@Service
public class PosComboService {

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
        if (!product.isCombo()) {
            stockService.checkAvailable(branchId, variantId, quantity);
            return;
        }
        List<ComboItem> items =
            comboItemRepository.findByComboProductIdAndStatusOrderByCreatedAtAsc(productId, "ACTIVE");
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
        if (!product.isCombo()) {
            stockService.reserve(branchId, variantId, quantity, orderId);
            return;
        }
        List<ComboItem> items =
            comboItemRepository.findByComboProductIdAndStatusOrderByCreatedAtAsc(productId, "ACTIVE");
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
        if (!product.isCombo()) {
            stockService.release(branchId, variantId, quantity, orderId);
            return;
        }
        List<ComboItem> items =
            comboItemRepository.findByComboProductIdAndStatusOrderByCreatedAtAsc(productId, "ACTIVE");
        for (ComboItem item : items) {
            stockService.release(branchId, item.getVariantId(), item.getQuantity() * quantity, orderId);
        }
    }
}
