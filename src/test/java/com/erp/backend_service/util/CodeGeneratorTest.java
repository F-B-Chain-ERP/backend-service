package com.erp.backend_service.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Khóa regression cho CodeGenerator: chuỗi tháng/ngày, lõi nextSequence,
 * chống trùng song song, fallback ngẫu nhiên, mã KH, chuẩn hóa.
 */
class CodeGeneratorTest {

    private static final YearMonth PERIOD = YearMonth.of(2026, 9);

    @Test
    @DisplayName("Tháng trống -> bắt đầu từ 0001")
    void monthlyStartsAtOne() {
        String code = CodeGenerator.nextMonthlySequence(
                "PO-", PERIOD, 4, 5, prefix -> Optional.empty(), c -> false);
        assertEquals("PO-202609-0001", code);
    }

    @Test
    @DisplayName("Nối tiếp số lớn nhất hiện có")
    void monthlyContinuesSequence() {
        Function<String, Optional<String>> last = prefix -> Optional.of(prefix + "0027");
        String code = CodeGenerator.nextMonthlySequence(
                "PO-", PERIOD, 4, 5, last, c -> false);
        assertEquals("PO-202609-0028", code);
    }

    @Test
    @DisplayName("Trùng do tạo song song -> nhảy số tiếp theo thay vì trùng")
    void monthlySkipsExisting() {
        Function<String, Optional<String>> last = prefix -> Optional.of(prefix + "0009");
        Set<String> busy = new HashSet<>(Set.of("SI-202609-0010", "SI-202609-0011"));
        Predicate<String> exists = busy::contains;
        String code = CodeGenerator.nextMonthlySequence(
                "SI-", PERIOD, 4, 5, last, exists);
        assertEquals("SI-202609-0012", code);
    }

    @Test
    @DisplayName("Hết lượt thử -> rơi về hậu tố ngẫu nhiên giữ tiền tố tháng")
    void monthlyFallsBackToRandom() {
        Function<String, Optional<String>> last = prefix -> Optional.of(prefix + "0001");
        String code = CodeGenerator.nextMonthlySequence(
                "SI-", PERIOD, 4, 2, last, c -> true);
        assertTrue(code.startsWith("SI-202609-"));
        assertEquals("SI-202609-".length() + 4, code.length());
    }

    @Test
    @DisplayName("Chuỗi theo ngày: PREFIX-yyyyMMdd-NNNN")
    void dailySequence() {
        String code = CodeGenerator.nextDailySequence(
                "SO-", LocalDate.of(2026, 9, 10), 4, 5, prefix -> Optional.empty(), c -> false);
        assertEquals("SO-20260910-0001", code);
    }

    @Test
    @DisplayName("Lõi nextSequence dùng được cho tiền tố ghép sẵn (chi nhánh/kho)")
    void coreSequenceWithComposedPrefix() {
        Function<String, Optional<String>> last = prefix -> Optional.of(prefix + "0041");
        String code = CodeGenerator.nextSequence(
                "HN01-PO-202609-", 4, 5, last, c -> false);
        assertEquals("HN01-PO-202609-0042", code);
    }

    @Test
    @DisplayName("Mã ngẫu nhiên đúng prefix + độ dài + in hoa")
    void randomFormat() {
        String code = CodeGenerator.random("TRF-", c -> false);
        assertTrue(code.startsWith("TRF-"));
        assertEquals("TRF-".length() + 8, code.length());
        assertEquals(code, code.toUpperCase());
    }

    @Test
    @DisplayName("Mã KH đúng format CUS- + 12 ký tự")
    void customerCodeFormat() {
        String code = CodeGenerator.customerCode();
        assertTrue(code.startsWith("CUS-"));
        assertEquals("CUS-".length() + 12, code.length());
    }

    @Test
    @DisplayName("Chuẩn hóa mã: trim + uppercase, null-safe")
    void normalizeCode() {
        assertEquals("CF-DEN", CodeGenerator.normalizeCode("  cf-den "));
        assertNull(CodeGenerator.normalizeCode(null));
    }

    @Test
    @DisplayName("Danh sách mã sinh liên tiếp không trùng nhau")
    void batchUniqueness() {
        Set<String> seen = new HashSet<>();
        Function<String, Optional<String>> last = prefix -> Optional.of(prefix + "0001");
        for (int i = 0; i < 50; i++) {
            String code = CodeGenerator.nextMonthlySequence(
                    "CNT-", PERIOD, 4, 60, last, seen::contains);
            assertTrue(seen.add(code), "Trùng mã: " + code);
        }
        List<String> sorted = seen.stream().sorted().toList();
        assertEquals("CNT-202609-0002", sorted.get(0));
    }
}
