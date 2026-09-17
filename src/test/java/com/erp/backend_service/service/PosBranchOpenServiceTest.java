package com.erp.backend_service.service;

import com.erp.backend_service.repository.BranchHoursRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.service.pos.PosBranchOpenService;
import com.erp.core.domain.Branch;
import com.erp.core.domain.BranchHours;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosBranchOpenServiceTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private BranchHoursRepository hoursRepository;

    private PosBranchOpenService posBranchOpenService;

    private UUID branchId;
    private ZoneId zone;

    @BeforeEach
    void setUp() {
        posBranchOpenService = new PosBranchOpenService(branchRepository, hoursRepository);
        branchId = UUID.randomUUID();
        zone = ZoneId.of("Asia/Ho_Chi_Minh");

        Branch branch = new Branch();
        branch.setId(branchId);
        branch.setTimezone("Asia/Ho_Chi_Minh");
        lenient().when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
    }

    @Test
    @DisplayName("Ca thường trong ngày (07:00 - 22:00): Đúng giờ mở trả về true, ngoài giờ trả về false")
    void testNormalShiftHours() {
        // Thứ Hai (dayOfWeek = 1)
        BranchHours mondayHours = new BranchHours();
        mondayHours.setBranchId(branchId);
        mondayHours.setDayOfWeek(1);
        mondayHours.setOpenTime(LocalTime.of(7, 0));
        mondayHours.setCloseTime(LocalTime.of(22, 0));
        mondayHours.setClosed(false);
        mondayHours.setStatus("ACTIVE");

        when(hoursRepository.findByBranchIdAndDayOfWeekAndStatus(branchId, 1, "ACTIVE"))
                .thenReturn(Optional.of(mondayHours));
        when(hoursRepository.findByBranchIdAndDayOfWeekAndStatus(branchId, 7, "ACTIVE"))
                .thenReturn(Optional.empty());

        // 1. Lúc 09:30 Thứ 2 -> Mở cửa
        ZonedDateTime insideTime = ZonedDateTime.of(LocalDate.of(2026, 9, 21), LocalTime.of(9, 30), zone); // 2026-09-21 là Thứ Hai
        assertTrue(posBranchOpenService.isOpenAt(branchId, insideTime));

        // 2. Lúc 06:30 Thứ 2 -> Chưa mở
        ZonedDateTime beforeTime = ZonedDateTime.of(LocalDate.of(2026, 9, 21), LocalTime.of(6, 30), zone);
        assertFalse(posBranchOpenService.isOpenAt(branchId, beforeTime));

        // 3. Lúc 22:30 Thứ 2 -> Đã đóng
        ZonedDateTime afterTime = ZonedDateTime.of(LocalDate.of(2026, 9, 21), LocalTime.of(22, 30), zone);
        assertFalse(posBranchOpenService.isOpenAt(branchId, afterTime));
    }

    @Test
    @DisplayName("Test Case 2 theo SRS: Ca qua đêm Thứ 6 (20:00 Thứ 6 - 02:00 Thứ 7)")
    void testOvernightShiftHoursTestCase2() {
        // Thứ 6 (dayOfWeek = 5): mở 20:00, đóng 02:00 sáng hôm sau
        BranchHours friHours = new BranchHours();
        friHours.setBranchId(branchId);
        friHours.setDayOfWeek(5);
        friHours.setOpenTime(LocalTime.of(20, 0));
        friHours.setCloseTime(LocalTime.of(2, 0));
        friHours.setClosed(false);
        friHours.setStatus("ACTIVE");

        // Thứ 7 (dayOfWeek = 6): mở 08:00, đóng 22:00
        BranchHours satHours = new BranchHours();
        satHours.setBranchId(branchId);
        satHours.setDayOfWeek(6);
        satHours.setOpenTime(LocalTime.of(8, 0));
        satHours.setCloseTime(LocalTime.of(22, 0));
        satHours.setClosed(false);
        satHours.setStatus("ACTIVE");

        when(hoursRepository.findByBranchIdAndDayOfWeekAndStatus(branchId, 5, "ACTIVE"))
                .thenReturn(Optional.of(friHours));
        when(hoursRepository.findByBranchIdAndDayOfWeekAndStatus(branchId, 6, "ACTIVE"))
                .thenReturn(Optional.of(satHours));

        // 2026-09-25 là Thứ Sáu, 2026-09-26 là Thứ Bảy

        // Case 2.1: Gọi lúc 22:30:00 tối Thứ 6 -> Kỳ vọng true
        ZonedDateTime case21 = ZonedDateTime.of(LocalDate.of(2026, 9, 25), LocalTime.of(22, 30), zone);
        assertTrue(posBranchOpenService.isOpenAt(branchId, case21), "22:30 tối Thứ 6 phải trả về true");

        // Case 2.2: Gọi lúc 01:15:00 sáng Thứ 7 -> Kỳ vọng true (ca Thứ 6 kéo dài)
        ZonedDateTime case22 = ZonedDateTime.of(LocalDate.of(2026, 9, 26), LocalTime.of(1, 15), zone);
        assertTrue(posBranchOpenService.isOpenAt(branchId, case22), "01:15 sáng Thứ 7 phải trả về true vì ca Thứ 6 kéo dài");

        // Case 2.3: Gọi lúc 03:30:00 sáng Thứ 7 -> Kỳ vọng false (ca Thứ 6 đã đóng lúc 02:00, Thứ 7 chưa mở)
        ZonedDateTime case23 = ZonedDateTime.of(LocalDate.of(2026, 9, 26), LocalTime.of(3, 30), zone);
        assertFalse(posBranchOpenService.isOpenAt(branchId, case23), "03:30 sáng Thứ 7 phải trả về false");
    }

    @Test
    @DisplayName("Ngày đóng cửa (is_closed = true): Luôn trả về false trong suốt ngày")
    void testClosedDay() {
        BranchHours sundayHours = new BranchHours();
        sundayHours.setBranchId(branchId);
        sundayHours.setDayOfWeek(7);
        sundayHours.setOpenTime(LocalTime.of(8, 0));
        sundayHours.setCloseTime(LocalTime.of(22, 0));
        sundayHours.setClosed(true);
        sundayHours.setStatus("ACTIVE");

        when(hoursRepository.findByBranchIdAndDayOfWeekAndStatus(branchId, 7, "ACTIVE"))
                .thenReturn(Optional.of(sundayHours));
        when(hoursRepository.findByBranchIdAndDayOfWeekAndStatus(branchId, 6, "ACTIVE"))
                .thenReturn(Optional.empty());

        // 2026-09-27 là Chủ Nhật
        ZonedDateTime sunNoon = ZonedDateTime.of(LocalDate.of(2026, 9, 27), LocalTime.of(12, 0), zone);
        assertFalse(posBranchOpenService.isOpenAt(branchId, sunNoon));
    }

    @Test
    @DisplayName("Fail-open: Nếu chi nhánh chưa có cấu hình giờ, coi như mở cửa")
    void testFailOpenWhenNoConfiguration() {
        when(hoursRepository.findByBranchIdAndDayOfWeekAndStatus(branchId, 1, "ACTIVE"))
                .thenReturn(Optional.empty());
        when(hoursRepository.findByBranchIdAndDayOfWeekAndStatus(branchId, 7, "ACTIVE"))
                .thenReturn(Optional.empty());

        ZonedDateTime mondayTime = ZonedDateTime.of(LocalDate.of(2026, 9, 21), LocalTime.of(10, 0), zone);
        assertTrue(posBranchOpenService.isOpenAt(branchId, mondayTime));
    }
}
