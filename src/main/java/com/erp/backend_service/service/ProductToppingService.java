package com.erp.backend_service.service;

import com.erp.core.dto.request.menu.AddProductToppingRequest;
import com.erp.core.dto.request.menu.UpdateProductToppingRequest;
import com.erp.core.dto.response.menu.ProductToppingResponse;

import java.util.List;
import java.util.UUID;

/**
 * Cung cấp nghiệp vụ quản lý liên kết topping–sản phẩm: gán, cập nhật cấu hình, gỡ topping.
 * Product-Topping xác định topping nào đi kèm sản phẩm, số lượng tối đa và có phải mặc định hay không.
 */
public interface ProductToppingService {

    /** Danh sách topping đã gán cho sản phẩm (bao gồm thông tin topping: mã, tên, giá, nhóm). */
    List<ProductToppingResponse> listByProduct(UUID productId);

    /** Gán topping vào sản phẩm. Kiểm tra sản phẩm ACTIVE, topping tồn tại, không trùng lặp. */
    ProductToppingResponse add(UUID productId, AddProductToppingRequest request);

    /** Cập nhật cấu hình topping trên sản phẩm (isDefault, maxQuantity). */
    ProductToppingResponse update(UUID id, UpdateProductToppingRequest request);

    /** Gỡ topping khỏi sản phẩm (hard delete). */
    void delete(UUID id);
}
