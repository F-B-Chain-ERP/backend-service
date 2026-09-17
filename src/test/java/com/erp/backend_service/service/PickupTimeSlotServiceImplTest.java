package com.erp.backend_service.service;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.OrderRepository;
import com.erp.backend_service.repository.PickupTimeSlotRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.impl.PickupTimeSlotServiceImpl;
import com.erp.core.domain.Branch;
import com.erp.core.domain.PickupTimeSlot;
import com.erp.core.dto.request.branch.CreatePickupTimeSlotRequest;
import com.erp.core.dto.request.branch.GeneratePickupSlotsRequest;
import com.erp.core.dto.response.branch.PickupTimeSlotResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PickupTimeSlotServiceImplTest {

    @Mock
    private PickupTimeSlotRepository slotRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DataScopeHelper dataScopeHelper;

    private PickupTimeSlotServiceImpl pickupTimeSlotService;

    private UUID branchId;
    private Branch branch;

    @BeforeEach
    void setUp() {
        pickupTimeSlotService = new PickupTimeSlotServiceImpl(
                slotRepository, branchRepository, orderRepository, dataScopeHelper);

        branchId = UUID.randomUUID();
        branch = new Branch();
        branch.setId(branchId);
        branch.setTimezone("Asia/Ho_Chi_Minh");

        lenient().when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
        lenient().when(branchRepository.existsById(branchId)).thenReturn(true);
    }

    @Test
    @DisplayName("Tạo mới khung giờ pickup thành công")
    void testCreateSlotSuccess() {
        CreatePickupTimeSlotRequest req = new CreatePickupTimeSlotRequest(
                "SLOT-0800-0830", LocalTime.of(8, 0), LocalTime.of(8, 30), 20, "ACTIVE");

        when(slotRepository.existsByBranchIdAndSlotCode(branchId, "SLOT-0800-0830")).thenReturn(false);
        when(slotRepository.save(any(PickupTimeSlot.class))).thenAnswer(inv -> {
            PickupTimeSlot s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        PickupTimeSlotResponse res = pickupTimeSlotService.createSlot(branchId, req);

        assertNotNull(res);
        assertEquals("SLOT-0800-0830", res.slotCode());
        assertEquals(20, res.maxOrders());
        verify(slotRepository).save(any(PickupTimeSlot.class));
    }

    @Test
    @DisplayName("Quy tắc BR-ORG-04: start_time >= end_time ném lỗi PICKUP_SLOT_400_INVALID_TIME_RANGE")
    void testCreateSlotInvalidStartAfterEnd() {
        CreatePickupTimeSlotRequest req = new CreatePickupTimeSlotRequest(
                "SLOT-2200-0200", LocalTime.of(22, 0), LocalTime.of(2, 0), 20, "ACTIVE");

        BaseException ex = assertThrows(BaseException.class, () -> pickupTimeSlotService.createSlot(branchId, req));
        assertEquals(ErrorCode.PICKUP_SLOT_400_INVALID_TIME_RANGE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Quy tắc BR-ORG-04: Thời lượng slot < 15 phút hoặc > 120 phút ném lỗi")
    void testCreateSlotInvalidDuration() {
        // 10 phút (< 15)
        CreatePickupTimeSlotRequest tooShort = new CreatePickupTimeSlotRequest(
                "SLOT-0800-0810", LocalTime.of(8, 0), LocalTime.of(8, 10), 20, "ACTIVE");
        BaseException exShort = assertThrows(BaseException.class, () -> pickupTimeSlotService.createSlot(branchId, tooShort));
        assertEquals(ErrorCode.PICKUP_SLOT_400_INVALID_TIME_RANGE, exShort.getErrorCode());

        // 150 phút (> 120)
        CreatePickupTimeSlotRequest tooLong = new CreatePickupTimeSlotRequest(
                "SLOT-0800-1030", LocalTime.of(8, 0), LocalTime.of(10, 30), 20, "ACTIVE");
        BaseException exLong = assertThrows(BaseException.class, () -> pickupTimeSlotService.createSlot(branchId, tooLong));
        assertEquals(ErrorCode.PICKUP_SLOT_400_INVALID_TIME_RANGE, exLong.getErrorCode());
    }

    @Test
    @DisplayName("Trùng slot_code trong cùng chi nhánh ném lỗi PICKUP_SLOT_409_CODE_EXISTS")
    void testCreateSlotDuplicateCode() {
        CreatePickupTimeSlotRequest req = new CreatePickupTimeSlotRequest(
                "SLOT-0800-0830", LocalTime.of(8, 0), LocalTime.of(8, 30), 20, "ACTIVE");

        when(slotRepository.existsByBranchIdAndSlotCode(branchId, "SLOT-0800-0830")).thenReturn(true);

        BaseException ex = assertThrows(BaseException.class, () -> pickupTimeSlotService.createSlot(branchId, req));
        assertEquals(ErrorCode.PICKUP_SLOT_409_CODE_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("Tự động sinh hàng loạt slot theo bước 30 phút thành công")
    void testGenerateSlots() {
        GeneratePickupSlotsRequest req = new GeneratePickupSlotsRequest(
                LocalTime.of(8, 0), LocalTime.of(10, 0), 30, 15);

        List<PickupTimeSlotResponse> result = pickupTimeSlotService.generateSlots(branchId, req);

        verify(slotRepository).saveAll(any());
        assertNotNull(result);
    }

    @Test
    @DisplayName("Lấy danh sách slot tính toán chính xác currentOrders và trạng thái isAvailable")
    void testGetSlotsWithCapacityCheck() {
        UUID slotId = UUID.randomUUID();
        PickupTimeSlot slot = new PickupTimeSlot();
        slot.setId(slotId);
        slot.setBranchId(branchId);
        slot.setSlotCode("SLOT-0900-0930");
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(9, 30));
        slot.setMaxOrders(5);
        slot.setStatus("ACTIVE");

        when(slotRepository.findByBranchIdOrderByStartTimeAsc(branchId)).thenReturn(List.of(slot));

        // Mock 5 đơn đã đặt trong slot này (đã đầy)
        Object[] countRow = new Object[]{slotId, 5L};
        List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(countRow);
        when(orderRepository.countActiveOrdersBySlotIdsOnDate(eq(branchId), any(), any(), any()))
                .thenReturn(rows);

        List<PickupTimeSlotResponse> slots = pickupTimeSlotService.getSlots(branchId, null, false);

        assertEquals(1, slots.size());
        PickupTimeSlotResponse slotRes = slots.get(0);
        assertEquals(5L, slotRes.currentOrders());
        assertFalse(slotRes.isAvailable(), "Slot đã đủ 5 đơn thì không còn khả dụng");
    }
}
