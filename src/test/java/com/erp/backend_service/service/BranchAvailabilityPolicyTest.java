package com.erp.backend_service.service;

import com.erp.core.domain.BranchProductAvailability;
import com.erp.core.domain.BranchToppingAvailability;
import com.erp.core.domain.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Quy ước sparse availability: thiếu dòng = mặc định bán.
 * Khóa regression cho lỗi chi nhánh mới trắng menu / 404 giỏ hàng.
 */
class BranchAvailabilityPolicyTest {

    @Test
    @DisplayName("Thiếu dòng availability món = mặc định bán, giá gốc")
    void missingProductRowMeansSellable() {
        assertTrue(BranchAvailabilityPolicy.isProductSellable(null));

        Product product = new Product();
        product.setBasePrice(BigDecimal.valueOf(34000));
        assertEquals(BigDecimal.valueOf(34000),
            BranchAvailabilityPolicy.productSalePrice(null, product));
    }

    @Test
    @DisplayName("Chỉ dòng tắt tường minh mới chặn bán món")
    void onlyExplicitOffBlocksProduct() {
        BranchProductAvailability on = new BranchProductAvailability();
        on.setAvailable(true);
        assertTrue(BranchAvailabilityPolicy.isProductSellable(on));

        BranchProductAvailability off = new BranchProductAvailability();
        off.setAvailable(false);
        assertFalse(BranchAvailabilityPolicy.isProductSellable(off));
    }

    @Test
    @DisplayName("Giá chi nhánh được ưu tiên khi có dòng salePrice")
    void branchSalePricePreferred() {
        Product product = new Product();
        product.setBasePrice(BigDecimal.valueOf(34000));

        BranchProductAvailability priced = new BranchProductAvailability();
        priced.setAvailable(true);
        priced.setSalePrice(BigDecimal.valueOf(30000));
        assertEquals(BigDecimal.valueOf(30000),
            BranchAvailabilityPolicy.productSalePrice(priced, product));
    }

    @Test
    @DisplayName("Thiếu dòng availability topping = mặc định hiện/bán")
    void missingToppingRowMeansVisible() {
        assertTrue(BranchAvailabilityPolicy.isToppingSellable(null));
        assertTrue(BranchAvailabilityPolicy.isToppingVisible(null));

        BranchToppingAvailability off = new BranchToppingAvailability();
        off.setStatus("ACTIVE");
        off.setAvailable(false);
        assertFalse(BranchAvailabilityPolicy.isToppingSellable(off));
        assertFalse(BranchAvailabilityPolicy.isToppingVisible(off));

        BranchToppingAvailability inactiveRow = new BranchToppingAvailability();
        inactiveRow.setStatus("INACTIVE");
        inactiveRow.setAvailable(true);
        assertFalse(BranchAvailabilityPolicy.isToppingVisible(inactiveRow));
    }
}
