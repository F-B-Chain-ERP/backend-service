package com.erp.backend_service.service;

import com.erp.core.dto.request.menu.CreateProductRequest;
import com.erp.core.dto.request.menu.UpdateProductRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.CreateProductResponse;
import com.erp.core.dto.response.menu.ProductDetailResponse;
import com.erp.core.dto.response.menu.ProductResponse;
import com.erp.core.dto.response.menu.ProductSalesResponse;

import java.util.UUID;

public interface ProductService {

    /**
     * Lấy danh sách sản phẩm cho quản trị viên (Admin ERP).
     *
     * @param page Số trang (0-indexed)
     * @param size Số phần tử mỗi trang
     * @param search Từ khóa tìm kiếm (mã hoặc tên món)
     * @param categoryId ID danh mục lọc (tuỳ chọn)
     * @param status Trạng thái (ACTIVE, INACTIVE, ...) (tuỳ chọn)
     * @param isFeatured Lọc món nổi bật (tuỳ chọn)
     * @param isBestSeller Lọc món bán chạy (tuỳ chọn)
     * @return Phân trang ProductResponse
     */
    PageResponse<ProductResponse> list(
            int page,
            int size,
            String search,
            UUID categoryId,
            String status,
            Boolean isFeatured,
            Boolean isBestSeller
    );

    /**
     * Lấy danh sách sản phẩm cho kênh bán hàng (Sales channel / Store POS & Web).
     * Chỉ trả về các sản phẩm ACTIVE, không yêu cầu xác thực người dùng.
     *
     * @param page Số trang (0-indexed)
     * @param size Số phần tử mỗi trang
     * @param search Từ khóa tìm kiếm (tuỳ chọn)
     * @param categoryId ID danh mục lọc (tuỳ chọn)
     * @param isFeatured Lọc món nổi bật (tuỳ chọn)
     * @return Phân trang ProductSalesResponse
     */
    PageResponse<ProductSalesResponse> listForSales(
            int page,
            int size,
            String search,
            UUID categoryId,
            Boolean isFeatured
    );

    /**
     * Lấy thông tin chi tiết một sản phẩm theo ID (kèm danh sách kích cỡ/variants) cho quản trị.
     *
     * @param id ID sản phẩm
     * @return Thông tin chi tiết sản phẩm ProductDetailResponse
     */
    ProductDetailResponse get(UUID id);

    /**
     * Lấy thông tin chi tiết một sản phẩm theo ID (kèm danh sách kích cỡ/variants) cho kênh bán hàng (public).
     *
     * @param id ID sản phẩm
     * @return Thông tin chi tiết sản phẩm ProductDetailResponse
     */
    ProductDetailResponse getDetailForSales(UUID id);

    /**
     * Tạo một sản phẩm thực đơn mới.
     *
     * @param request Thông tin sản phẩm cần tạo (đã bao gồm imageUrl sau khi upload)
     * @return Phản hồi tóm tắt CreateProductResponse (HTTP 201)
     */
    CreateProductResponse create(CreateProductRequest request);

    /**
     * Cập nhật thông tin sản phẩm thực đơn.
     *
     * @param id ID sản phẩm cần cập nhật
     * @param request Thông tin sản phẩm cập nhật
     * @return ProductResponse chi tiết sản phẩm sau cập nhật
     */
    ProductResponse update(UUID id, UpdateProductRequest request);

    /**
     * Xóa mềm sản phẩm thực đơn (chuyển trạng thái sang DELETED).
     *
     * @param id ID sản phẩm cần xóa mềm
     */
    void delete(UUID id);
}
