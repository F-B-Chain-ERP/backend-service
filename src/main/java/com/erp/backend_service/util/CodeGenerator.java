package com.erp.backend_service.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Sinh mã dùng chung toàn hệ thống: chứng từ (PO/SI/SO/TRF/CNT), khách hàng (CUS),
 * sau này là mã NVL/SP/lô hàng... mà không cần sửa gì thêm.
 *
 * <p>Quy ước đặt mã mới chỉ cần chốt 3 thứ:
 * <ol>
 *   <li>Tiền tố (có thể ghép sẵn chi nhánh/kho, ví dụ {@code "HN01-PO-"}).</li>
 *   <li>Phần kỳ (tháng {@code yyyyMM} / ngày {@code yyyyMMdd}) hoặc không kỳ (ngẫu nhiên).</li>
 *   <li>Đuôi: số thứ tự zero-pad hoặc chuỗi ngẫu nhiên.</li>
 * </ol>
 *
 * <p>Chống trùng khi tạo song song: kiểm tra tồn tại sau mỗi lần sinh, hết lượt
 * thì rơi về hậu tố ngẫu nhiên (vẫn giữ tiền tố + kỳ để nhận diện). An toàn mức
 * ứng dụng; muốn đảm bảo tuyệt đối thì cột mã trong DB phải có unique constraint.
 *
 * <p>Không phụ thuộc Spring/JPA: tầng gọi truyền 2 hàm của repository nhà mình
 * (tìm mã lớn nhất theo tiền tố + kiểm tra tồn tại). Không trạng thái dùng chung
 * nên an toàn đa luồng.
 */
public final class CodeGenerator {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int DEFAULT_SEQUENCE_WIDTH = 4;
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int RANDOM_FALLBACK_LENGTH = 4;
    private static final int DEFAULT_RANDOM_LENGTH = 8;
    private static final int CUSTOMER_CODE_LENGTH = 12;

    private CodeGenerator() {
    }

    // ── Chuỗi theo tháng: PREFIX-yyyyMM-NNNN ─────────────────────────────

    /**
     * Sinh mã chuỗi theo tháng hiện tại, độ rộng 4, thử tối đa 5 lần.
     *
     * @param prefix               tiền tố (ví dụ {@code "PO-"}).
     * @param findLastCodeByPrefix tìm mã lớn nhất đang có với tiền tố kỳ.
     * @param existsByCode         kiểm tra mã đã tồn tại.
     */
    public static String nextMonthlySequence(
            String prefix,
            Function<String, Optional<String>> findLastCodeByPrefix,
            Predicate<String> existsByCode
    ) {
        return nextMonthlySequence(prefix, YearMonth.now(), DEFAULT_SEQUENCE_WIDTH,
                DEFAULT_MAX_ATTEMPTS, findLastCodeByPrefix, existsByCode);
    }

    /**
     * Sinh mã chuỗi theo kỳ cho trước (quyết định, dễ test).
     */
    public static String nextMonthlySequence(
            String prefix,
            YearMonth period,
            int sequenceWidth,
            int maxAttempts,
            Function<String, Optional<String>> findLastCodeByPrefix,
            Predicate<String> existsByCode
    ) {
        return nextSequence(prefix + period.format(MONTH_FORMAT) + "-",
                sequenceWidth, maxAttempts, findLastCodeByPrefix, existsByCode);
    }

    // ── Chuỗi theo ngày: PREFIX-yyyyMMdd-NNNN (để dành mở rộng) ──────────

    /**
     * Sinh mã chuỗi theo ngày hiện tại.
     */
    public static String nextDailySequence(
            String prefix,
            Function<String, Optional<String>> findLastCodeByPrefix,
            Predicate<String> existsByCode
    ) {
        return nextDailySequence(prefix, LocalDate.now(), DEFAULT_SEQUENCE_WIDTH,
                DEFAULT_MAX_ATTEMPTS, findLastCodeByPrefix, existsByCode);
    }

    /**
     * Sinh mã chuỗi theo ngày cho trước.
     */
    public static String nextDailySequence(
            String prefix,
            LocalDate date,
            int sequenceWidth,
            int maxAttempts,
            Function<String, Optional<String>> findLastCodeByPrefix,
            Predicate<String> existsByCode
    ) {
        return nextSequence(prefix + date.format(DAY_FORMAT) + "-",
                sequenceWidth, maxAttempts, findLastCodeByPrefix, existsByCode);
    }

    /**
     * Lõi sinh chuỗi với tiền tố đã bao gồm kỳ: nối tiếp số lớn nhất, trùng thì
     * nhảy tiếp, hết lượt thì hậu tố ngẫu nhiên.
     */
    public static String nextSequence(
            String prefixWithPeriod,
            int sequenceWidth,
            int maxAttempts,
            Function<String, Optional<String>> findLastCodeByPrefix,
            Predicate<String> existsByCode
    ) {
        int next = findLastCodeByPrefix.apply(prefixWithPeriod)
                .map(last -> parseSequence(last, prefixWithPeriod))
                .orElse(1);

        for (int attempt = 0; attempt < Math.max(maxAttempts, 1); attempt++, next++) {
            String candidate = prefixWithPeriod + leftPad(next, sequenceWidth);
            if (!existsByCode.test(candidate)) {
                return candidate;
            }
        }
        return prefixWithPeriod + randomPart(RANDOM_FALLBACK_LENGTH);
    }

    // ── Ngẫu nhiên: PREFIX-XXXXXXXX ──────────────────────────────────────

    /**
     * Sinh mã ngẫu nhiên (mặc định 8 ký tự hex), thử lại khi trùng.
     */
    public static String random(String prefix, Predicate<String> existsByCode) {
        return random(prefix, DEFAULT_RANDOM_LENGTH, existsByCode);
    }

    /** Sinh mã ngẫu nhiên, không kiểm tra tồn tại (chỉ dùng khi độ dài đủ lớn). */
    public static String random(String prefix) {
        return prefix + randomPart(DEFAULT_RANDOM_LENGTH);
    }

    /**
     * Sinh mã ngẫu nhiên với độ dài tùy chọn, có kiểm tra tồn tại.
     */
    public static String random(String prefix, int randomLength, Predicate<String> existsByCode) {
        for (int attempt = 0; attempt < Math.max(DEFAULT_MAX_ATTEMPTS, 1); attempt++) {
            String candidate = prefix + randomPart(randomLength);
            if (!existsByCode.test(candidate)) {
                return candidate;
            }
        }
        return prefix + randomPart(Math.max(randomLength, DEFAULT_RANDOM_LENGTH))
                + randomPart(RANDOM_FALLBACK_LENGTH);
    }

    // ── Tiện ích ─────────────────────────────────────────────────────────

    /** Sinh mã khách hàng {@code CUS-XXXXXXXXXXXX} (giữ đúng format đang dùng). */
    public static String customerCode(Predicate<String> existsByCode) {
        return random("CUS-", CUSTOMER_CODE_LENGTH, existsByCode);
    }

    /** Sinh mã khách hàng, không kiểm tra tồn tại (giữ đúng hành vi code cũ). */
    public static String customerCode() {
        return "CUS-" + randomPart(CUSTOMER_CODE_LENGTH);
    }

    /** Chuẩn hóa mã do người dùng nhập: cắt khoảng trắng + in hoa, null-safe. */
    public static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    private static int parseSequence(String code, String prefixWithPeriod) {
        try {
            return Integer.parseInt(code.substring(prefixWithPeriod.length())) + 1;
        } catch (RuntimeException e) {
            return 1;
        }
    }

    private static String leftPad(int value, int width) {
        String raw = String.valueOf(Math.max(value, 0));
        if (raw.length() >= width) {
            return raw;
        }
        return "0".repeat(width - raw.length()) + raw;
    }

    private static String randomPart(int length) {
        String hex = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        StringBuilder result = new StringBuilder(length);
        while (result.length() < length) {
            result.append(hex);
            hex = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        }
        return result.substring(0, length);
    }
}
