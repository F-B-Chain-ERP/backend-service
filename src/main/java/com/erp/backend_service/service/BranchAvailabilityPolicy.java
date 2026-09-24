package com.erp.backend_service.service;

import com.erp.core.domain.BranchProductAvailability;
import com.erp.core.domain.BranchToppingAvailability;
import com.erp.core.domain.Product;

import java.math.BigDecimal;

/**
 * Quy ước khả dụng theo chi nhánh, tập trung một chỗ để không phải vá từng màn
 * mỗi khi thêm chi nhánh / món / topping mới.
 *
 * <p>Bảng availability là thưa (sparse): chỉ lưu override. Thiếu dòng =
 * mặc định bán. Chỉ dòng {@code ACTIVE + isAvailable=false} (tắt tường minh)
 * mới ẩn/chặn.</p>
 */
public final class BranchAvailabilityPolicy {

    private BranchAvailabilityPolicy() {
    }

    /** Món có được bán tại chi nhánh không. Query đầu vào nên lọc status=ACTIVE. */
    public static boolean isProductSellable(BranchProductAvailability bpa) {
        return bpa == null || bpa.isAvailable();
    }

    /** Topping có được bán tại chi nhánh không. Query đầu vào nên lọc status=ACTIVE. */
    public static boolean isToppingSellable(BranchToppingAvailability bta) {
        return bta == null || bta.isAvailable();
    }

    /**
     * Topping có được hiện trong menu bán hàng không. Dùng cho query KHÔNG lọc
     * status (tự check ACTIVE tay khi có dòng).
     */
    public static boolean isToppingVisible(BranchToppingAvailability bta) {
        if (bta == null) {
            return true;
        }
        return "ACTIVE".equals(bta.getStatus()) && bta.isAvailable();
    }

    /** Giá bán: ưu tiên giá chi nhánh, thiếu dòng thì giá gốc sản phẩm. */
    public static BigDecimal productSalePrice(BranchProductAvailability bpa, Product product) {
        if (bpa != null && bpa.getSalePrice() != null) {
            return bpa.getSalePrice();
        }
        return product.getBasePrice();
    }
}
