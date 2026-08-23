package com.erp.backend_service.service;

import com.erp.core.dto.auth.AccountResponse;
import com.erp.core.dto.auth.CreateAccountRequest;
import com.erp.core.dto.auth.ResetPasswordRequest;
import com.erp.core.dto.auth.UpdateAccountRequest;
import com.erp.core.dto.response.PageResponse;

import java.util.UUID;

/**
 * Định nghĩa các nghiệp vụ quản lý tài khoản nội bộ (account).
 * Tài khoản nội bộ chỉ do admin cấp, không tự đăng ký.
 */
public interface AccountService {

    /**
     * Tạo tài khoản nội bộ mới do admin cấp.
     *
     * @param request thông tin tài khoản
     * @return tài khoản vừa tạo
     */
    AccountResponse createAccount(CreateAccountRequest request);

    /**
     * Lấy thông tin một tài khoản theo id.
     *
     * @param id id tài khoản
     * @return thông tin tài khoản
     */
    AccountResponse getAccount(UUID id);

    /**
     * Lấy danh sách tài khoản phân trang, có tìm kiếm theo username/tên/email.
     *
     * @param page   số trang (bắt đầu từ 0)
     * @param size   kích thước trang
     * @param search từ khóa tìm kiếm (có thể null/rỗng)
     * @return trang kết quả tài khoản
     */
    PageResponse<AccountResponse> listAccounts(int page, int size, String search);

    /**
     * Cập nhật thông tin tài khoản (chỉ áp dụng các trường khác null).
     *
     * @param id      id tài khoản
     * @param request các trường cần cập nhật
     * @return tài khoản sau khi cập nhật
     */
    AccountResponse updateAccount(UUID id, UpdateAccountRequest request);

    /**
     * Vô hiệu hóa (xóa mềm) tài khoản và thu hồi phiên đang hoạt động.
     *
     * @param id id tài khoản
     */
    void deleteAccount(UUID id);

    /**
     * Đặt lại mật khẩu cho tài khoản và thu hồi các phiên hiện tại.
     *
     * @param id      id tài khoản
     * @param request mật khẩu mới
     * @return tài khoản sau khi đặt lại mật khẩu
     */
    AccountResponse resetPassword(UUID id, ResetPasswordRequest request);
}
