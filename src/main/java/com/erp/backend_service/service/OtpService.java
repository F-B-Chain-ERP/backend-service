package com.erp.backend_service.service;

import com.erp.core.enums.OtpPurpose;

import java.util.UUID;

/**
 * Cung cấp nghiệp vụ sinh, gửi và xác thực mã OTP xác thực email của khách hàng.
 * Mã OTP được lưu trên Redis kèm TTL, đồng thời gửi qua email.
 */
public interface OtpService {

    /**
     * Sinh mã OTP an toàn, lưu vào Redis (có TTL) và gửi tới email tương ứng.
     * Tuân thủ cooldown giữa các lần gửi.
     *
     * @param principalId id thực thể (account hoặc customer)
     * @param email       email nhận mã
     * @param fullName    họ tên đầy đủ (dùng trong nội dung email)
     * @param purpose     mục đích sử dụng OTP (đăng ký / đặt lại mật khẩu)
     */
    void generateAndSendOtp(UUID principalId, String email, String fullName, OtpPurpose purpose);

    /**
     * Xác thực mã OTP của thực thể theo mục đích. Ném lỗi nếu mã sai, hết hạn
     * hoặc vượt quá số lần thử. Khi thành công sẽ xoá mã khỏi Redis.
     *
     * @param principalId id thực thể
     * @param otp         mã OTP do client gửi lên
     * @param purpose     mục đích sử dụng OTP
     */
    void verifyOtp(UUID principalId, String otp, OtpPurpose purpose);
}
