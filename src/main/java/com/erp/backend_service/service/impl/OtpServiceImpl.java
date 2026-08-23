package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BadRequestException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.service.MailService;
import com.erp.core.enums.OtpPurpose;
import com.erp.backend_service.service.OtpService;
import com.erp.backend_service.util.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Triển khai {@link OtpService}: sinh OTP an toàn, lưu trên Redis với TTL 15 phút,
 * giới hạn số lần thử và cooldown gửi lại, đồng thời gửi qua email Thymeleaf.
 */
@Service
public class OtpServiceImpl implements OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpServiceImpl.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final MailService mailService;
    private final String brandName;
    private final int otpLength;
    private final Duration otpExpiry;
    private final int maxAttempts;
    private final Duration resendCooldown;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpServiceImpl(StringRedisTemplate stringRedisTemplate,
                          MailService mailService,
                          @Value("${app.mail.from-name}") String brandName,
                          @Value("${app.otp.length:6}") int otpLength,
                          @Value("${app.otp.expiry-seconds:900}") long otpExpirySeconds,
                          @Value("${app.otp.max-attempts:5}") int maxAttempts,
                          @Value("${app.otp.resend-cooldown-seconds:60}") long resendCooldownSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.mailService = mailService;
        this.brandName = brandName;
        this.otpLength = otpLength;
        this.otpExpiry = Duration.ofSeconds(otpExpirySeconds);
        this.maxAttempts = maxAttempts;
        this.resendCooldown = Duration.ofSeconds(resendCooldownSeconds);
    }

    /** {@inheritDoc} */
    @Override
    public void generateAndSendOtp(UUID principalId, String email, String fullName, OtpPurpose purpose) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException(ErrorCode.EMAIL_REQUIRED);
        }

        String otp = generateOtp();
        try {
            String cooldownKey = cooldownKey(purpose, principalId);
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey))) {
                throw new BadRequestException(ErrorCode.TOO_MANY_REQUESTS);
            }
            String otpKey = otpKey(purpose, principalId);
            stringRedisTemplate.opsForValue().set(otpKey, otp, otpExpiry);
            stringRedisTemplate.delete(attemptsKey(purpose, principalId));
            stringRedisTemplate.opsForValue().set(cooldownKey, "1", resendCooldown);
            log.info("Đã sinh OTP [{}] cho thực thể {} (email={})", purpose, principalId, email);
        } catch (BadRequestException e) {
            // Lỗi nghiệp vụ (vượt ngưỡng cooldown) -> ném tiếp để client biết.
            throw e;
        } catch (Exception e) {
            // Redis không khả dụng: coi OTP là best-effort (như gửi email),
            // không làm sập luồng đăng ký/xác thực. Log mã OTP ra console để dev test.
            log.error("Lưu/gửi OTP thất bại (Redis chưa cấu hình/sẵn sàng?): {}. OTP dev = {}",
                    email, otp, e);
            return;
        }

        sendOtpEmail(email, fullName, otp, purpose);
    }

    /** {@inheritDoc} */
    @Override
    public void verifyOtp(UUID principalId, String otp, OtpPurpose purpose) {
        String otpKey = otpKey(purpose, principalId);
        String stored = stringRedisTemplate.opsForValue().get(otpKey);
        if (stored == null) {
            throw new BadRequestException(ErrorCode.OTP_EXPIRED);
        }

        String attemptsKey = attemptsKey(purpose, principalId);
        long attempts = stringRedisTemplate.opsForValue().increment(attemptsKey, 1);
        if (attempts == 1) {
            stringRedisTemplate.expire(attemptsKey, otpExpiry);
        }
        if (attempts > maxAttempts) {
            stringRedisTemplate.delete(otpKey);
            stringRedisTemplate.delete(attemptsKey);
            throw new BadRequestException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        }
        if (otp == null || !stored.equals(otp)) {
            throw new BadRequestException(ErrorCode.OTP_INVALID);
        }

        stringRedisTemplate.delete(otpKey);
        stringRedisTemplate.delete(attemptsKey);
        stringRedisTemplate.delete(cooldownKey(purpose, principalId));
        log.info("Xác thực OTP [{}] thành công cho thực thể {}", purpose, principalId);
    }

    /** Sinh chuỗi OTP gồm {@code otpLength} chữ số. */
    private String generateOtp() {
        StringBuilder builder = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            builder.append(secureRandom.nextInt(10));
        }
        return builder.toString();
    }

    /** Render và gửi email chứa mã OTP theo mục đích. */
    private void sendOtpEmail(String email, String fullName, String otp, OtpPurpose purpose) {
        boolean isReset = purpose == OtpPurpose.PASSWORD_RESET;
        String title = isReset ? "Đặt lại mật khẩu" : "Xác thực email";
        String message = isReset
                ? "Sử dụng mã dưới đây để đặt lại mật khẩu tài khoản của bạn:"
                : "Đây là mã xác thực email của bạn:";
        String subject = (isReset ? "Đặt lại mật khẩu" : "Mã xác thực email") + " - " + brandName;

        String name = fullName != null ? fullName : email;
        Map<String, Object> variables = Map.of(
                "fullName", name,
                "otp", otp,
                "title", title,
                "message", message,
                "appName", brandName,
                "expiryMinutes", String.valueOf(otpExpiry.toMinutes()),
                "year", String.valueOf(java.time.Year.now().getValue())
        );
        try {
            mailService.sendTemplate(email, subject, "mail/otp-verification", variables);
        } catch (Exception e) {
            // Không chặn luồng đăng ký/xác thực khi gửi email thất bại (SMTP chưa cấu hình ở local).
            // Log mã OTP ra console để dev test thủ công.
            log.error("Gửi email OTP thất bại (SMTP chưa cấu hình?): {}. OTP dev = {}", email, otp, e);
        }
    }

    /** Khóa Redis lưu OTP theo mục đích. */
    private String otpKey(OtpPurpose purpose, UUID principalId) {
        return purpose == OtpPurpose.PASSWORD_RESET
                ? RedisKeys.passwordResetOtp(principalId)
                : RedisKeys.otpRegister(principalId);
    }

    /** Khóa Redis đếm số lần thử sai theo mục đích. */
    private String attemptsKey(OtpPurpose purpose, UUID principalId) {
        return purpose == OtpPurpose.PASSWORD_RESET
                ? RedisKeys.passwordResetAttempts(principalId)
                : RedisKeys.otpAttempts(principalId);
    }

    /** Khóa Redis giới hạn cooldown gửi lại theo mục đích. */
    private String cooldownKey(OtpPurpose purpose, UUID principalId) {
        return purpose == OtpPurpose.PASSWORD_RESET
                ? RedisKeys.passwordResetCooldown(principalId)
                : RedisKeys.otpCooldown(principalId);
    }
}
