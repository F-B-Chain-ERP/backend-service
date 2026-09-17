package com.erp.backend_service.service;

import com.erp.core.dto.response.menu.ProductToppingResponse;

import java.util.List;
import java.util.UUID;

/**
 * Topping cho kênh bán hàng (public): chỉ topping ACTIVE đã gán cho SP,
 * có branchId thì lọc thêm khả dụng tại chi nhánh.
 */
public interface SalesToppingService {
    List<ProductToppingResponse> listForSales(UUID productId, UUID branchId);
}
