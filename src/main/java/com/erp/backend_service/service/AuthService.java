package com.erp.backend_service.service;

import com.erp.core.dto.auth.AuthResponse;
import com.erp.core.dto.auth.ChangePasswordRequest;
import com.erp.core.dto.auth.ForgotPasswordRequest;
import com.erp.core.dto.auth.GoogleOAuth2Request;
import com.erp.core.dto.auth.LoginRequest;
import com.erp.core.dto.auth.RefreshTokenRequest;
import com.erp.core.dto.auth.RegisterCustomerRequest;
import com.erp.core.dto.auth.ResendOtpRequest;
import com.erp.core.dto.auth.ResetPasswordOtpRequest;
import com.erp.core.dto.auth.SelectBranchRequest;
import com.erp.core.dto.auth.VerifyOtpRequest;

/**
 * Cung cấp các nghiệp vụ xác thực: đăng ký khách hàng, đăng nhập, làm mới token
 * và đăng xuất (cho cả tài khoản nội bộ và khách hàng).
 */
public interface AuthService {

    /**
     * Xác thực thông tin đăng nhập và cấp cặp access/refresh token.
     * Loại thực thể (ACCOUNT / CUSTOMER) được chỉ định trong {@link LoginRequest}.
     *
     * @param request tên đăng nhập/email/phone và mật khẩu
     * @return kết quả xác thực gồm token và thông tin thực thể
     */
    AuthResponse login(LoginRequest request);

    /**
     * Đăng ký tài khoản khách hàng (customer). Khách hàng tự đăng ký;
     * tài khoản nội bộ không dùng phương thức này.
     *
     * @param request thông tin đăng ký
     * @return kết quả xác thực (tự động đăng nhập)
     */
    AuthResponse registerCustomer(RegisterCustomerRequest request);

    /**
     * Xác thực khách hàng qua Google OAuth2: xác minh ID token, tự động tạo
     * (hoặc liên kết) tài khoản khách hàng và cấp cặp token ứng dụng.
     * Tài khoản Google được tự động đánh dấu email đã xác thực.
     *
     * @param request Google ID token từ phía client
     * @return kết quả xác thực (tự động đăng nhập)
     */
    AuthResponse authenticateWithGoogle(GoogleOAuth2Request request);

    /**
     * Xác thực mã OTP email của phiên đăng ký / đăng nhập.
     * Khi thành công, đánh dấu email đã xác thực và cấp cặp token ứng dụng.
     *
     * @param request token phiên đăng ký và mã OTP
     * @return kết quả xác thực (đã xác thực email)
     */
    AuthResponse verifyEmailOtp(VerifyOtpRequest request);

    /**
     * Gửi lại mã OTP xác thực email cho phiên đăng ký hiện tại.
     *
     * @param request token phiên đăng ký hiện tại
     * @return kết quả chứa verifyToken mới (chưa cấp token truy cập)
     */
    AuthResponse resendRegistrationOtp(ResendOtpRequest request);

    /**
     * Yêu cầu đặt lại mật khẩu (quên mật khẩu): gửi mã OTP qua email và trả về
     * reset token (phiên đặt lại mật khẩu).
     *
     * @param request email và loại thực thể
     * @return kết quả chứa reset token (verifyToken) để dùng ở bước đặt lại
     */
    AuthResponse forgotPassword(ForgotPasswordRequest request);

    /**
     * Đặt lại mật khẩu bằng mã OTP: xác thực reset token + OTP, cập nhật mật khẩu
     * mới và cấp cặp token (tự động đăng nhập).
     *
     * @param request reset token, mã OTP và mật khẩu mới
     * @return kết quả xác thực (đã đăng nhập)
     */
    AuthResponse resetPassword(ResetPasswordOtpRequest request);

    /**
     * Đổi mật khẩu của người dùng đang đăng nhập (cần mật khẩu cũ để xác thực).
     * Áp dụng cho cả ACCOUNT và CUSTOMER. Trả về cặp token mới.
     *
     * @param request mật khẩu cũ và mật khẩu mới
     * @return kết quả xác thực (đã đăng nhập với token mới)
     */
    AuthResponse changePassword(ChangePasswordRequest request);

    /**
     * Dùng refresh token hợp lệ để cấp cặp token mới (xoay vòng single-use).
     *
     * @param request refresh token hiện tại
     * @return kết quả xác thực với token mới
     */
    AuthResponse refreshToken(RefreshTokenRequest request);

    /**
     * Đăng xuất: thu hồi access token (với account) và refresh token tương ứng.
     *
     * @param accessToken  access token (có thể mang tiền tố "Bearer ")
     * @param refreshToken refresh token cần thu hồi (có thể null)
     */
    void logout(String accessToken, String refreshToken);

    /**
     * Chọn đơn vị (chi nhánh) làm việc cho tài khoản đã đăng nhập và cấp cặp
     * token mới (access token chứa branchId đã chọn). Branch phải nằm trong
     * phạm vi (scope) được gán.
     *
     * @param request branch được chọn
     * @return kết quả xác thực với access token mới chứa branchId
     */
    AuthResponse selectBranch(SelectBranchRequest request);
}
