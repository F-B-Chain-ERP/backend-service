package com.erp.backend_service.service;

import com.erp.core.dto.auth.CreateCustomerRequest;
import com.erp.core.dto.auth.ResetCustomerPasswordRequest;
import com.erp.core.dto.auth.UpdateCustomerRequest;
import com.erp.core.dto.response.CustomerDetailResponse;
import com.erp.core.dto.response.PageResponse;

import java.util.UUID;

/**
 * Định nghĩa các nghiệp vụ quản lý tài khoản khách hàng (customer) bởi admin nội bộ.
 * Khách hàng hoàn toàn tách biệt với tài khoản nội bộ (account).
 */
public interface CustomerService {

    /**
     * Tạo tài khoản khách hàng mới do admin cấp.
     *
     * @param request thông tin khách hàng
     * @return khách hàng vừa tạo
     */
    CustomerDetailResponse createCustomer(CreateCustomerRequest request);

    /**
     * Lấy thông tin một khách hàng theo id.
     *
     * @param id id khách hàng
     * @return thông tin khách hàng
     */
    CustomerDetailResponse getCustomer(UUID id);

    /**
     * Lấy danh sách khách hàng phân trang, có tìm kiếm theo mã/tên/phone/email.
     *
     * @param page   số trang (bắt đầu từ 0)
     * @param size   kích thước trang
     * @param search từ khóa tìm kiếm (có thể null/rỗng)
     * @return trang kết quả khách hàng
     */
    PageResponse<CustomerDetailResponse> listCustomers(int page, int size, String search);

    /**
     * Cập nhật thông tin khách hàng (chỉ áp dụng các trường khác null).
     *
     * @param id      id khách hàng
     * @param request các trường cần cập nhật
     * @return khách hàng sau khi cập nhật
     */
    CustomerDetailResponse updateCustomer(UUID id, UpdateCustomerRequest request);

    /**
     * Vô hiệu hóa (xóa mềm) khách hàng và thu hồi các phiên đang hoạt động.
     *
     * @param id id khách hàng
     */
    void deleteCustomer(UUID id);

    /**
     * Đặt lại mật khẩu cho khách hàng và thu hồi các phiên hiện tại.
     *
     * @param id      id khách hàng
     * @param request mật khẩu mới
     * @return khách hàng sau khi đặt lại mật khẩu
     */
    CustomerDetailResponse resetPassword(UUID id, ResetCustomerPasswordRequest request);
}
