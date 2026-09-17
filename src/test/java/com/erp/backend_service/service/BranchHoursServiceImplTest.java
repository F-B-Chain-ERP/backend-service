package com.erp.backend_service.service;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.BranchHoursRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.impl.BranchHoursServiceImpl;
import com.erp.core.domain.BranchHours;
import com.erp.core.dto.request.branch.BatchUpdateBranchHoursRequest;
import com.erp.core.dto.request.branch.UpdateBranchHoursItemRequest;
import com.erp.core.dto.response.branch.BranchHoursResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchHoursServiceImplTest {

    @Mock
    private BranchHoursRepository hoursRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private DataScopeHelper dataScopeHelper;

    private BranchHoursServiceImpl branchHoursService;
    private UUID branchId;

    @BeforeEach
    void setUp() {
        branchHoursService = new BranchHoursServiceImpl(hoursRepository, branchRepository, dataScopeHelper);
        branchId = UUID.randomUUID();
        when(branchRepository.existsById(branchId)).thenReturn(true);
    }

    @Test
    @DisplayName("Lấy danh sách 7 ngày trong tuần của chi nhánh thành công")
    void testGetHoursSuccess() {
        List<BranchHours> mockList = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            BranchHours bh = new BranchHours();
            bh.setId(UUID.randomUUID());
            bh.setBranchId(branchId);
            bh.setDayOfWeek(i);
            bh.setOpenTime(LocalTime.of(7, 0));
            bh.setCloseTime(LocalTime.of(22, 0));
            bh.setClosed(false);
            bh.setStatus("ACTIVE");
            mockList.add(bh);
        }

        when(hoursRepository.findByBranchIdOrderByDayOfWeekAsc(branchId)).thenReturn(mockList);

        List<BranchHoursResponse> result = branchHoursService.getHours(branchId);

        assertEquals(7, result.size());
        assertEquals("Thứ Hai", result.get(0).dayName());
        assertEquals("Chủ Nhật", result.get(6).dayName());
        verify(dataScopeHelper).enforceBranchAccess(branchId);
    }

    @Test
    @DisplayName("Cập nhật batch giờ tuần hợp lệ")
    void testUpdateHoursSuccess() {
        BranchHours mon = new BranchHours();
        mon.setBranchId(branchId);
        mon.setDayOfWeek(1);
        mon.setOpenTime(LocalTime.of(7, 0));
        mon.setCloseTime(LocalTime.of(22, 0));

        when(hoursRepository.findByBranchIdOrderByDayOfWeekAsc(branchId)).thenReturn(List.of(mon));

        List<UpdateBranchHoursItemRequest> items = List.of(
                new UpdateBranchHoursItemRequest(1, LocalTime.of(8, 0), LocalTime.of(23, 0), false, "ACTIVE")
        );
        BatchUpdateBranchHoursRequest req = new BatchUpdateBranchHoursRequest(items);

        List<BranchHoursResponse> responses = branchHoursService.updateHours(branchId, req);

        verify(hoursRepository).saveAll(any());
        verify(dataScopeHelper).enforceBranchAccess(branchId);
    }

    @Test
    @DisplayName("Quy tắc BR-ORG-03: Vi phạm openTime == closeTime khi không đánh dấu is_closed ném lỗi")
    void testInvalidTimesThrowsError() {
        List<UpdateBranchHoursItemRequest> items = List.of(
                new UpdateBranchHoursItemRequest(1, LocalTime.of(8, 0), LocalTime.of(8, 0), false, "ACTIVE")
        );
        BatchUpdateBranchHoursRequest req = new BatchUpdateBranchHoursRequest(items);

        BaseException ex = assertThrows(BaseException.class, () -> branchHoursService.updateHours(branchId, req));
        assertEquals(ErrorCode.BRANCH_HOURS_400_INVALID_TIMES, ex.getErrorCode());
    }

    @Test
    @DisplayName("Test Case 1 theo SRS: Chặn truy cập chéo dữ liệu chi nhánh qua DataScopeHelper")
    void testCrossScopeDenied() {
        doThrow(new BaseException(ErrorCode.CROSS_SCOPE_DENIED))
                .when(dataScopeHelper).enforceBranchAccess(branchId);

        BaseException ex = assertThrows(BaseException.class, () -> branchHoursService.getHours(branchId));
        assertEquals(ErrorCode.CROSS_SCOPE_DENIED, ex.getErrorCode());
    }
}
