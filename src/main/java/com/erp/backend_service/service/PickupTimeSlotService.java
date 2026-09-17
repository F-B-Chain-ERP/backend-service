package com.erp.backend_service.service;

import com.erp.core.dto.request.branch.CreatePickupTimeSlotRequest;
import com.erp.core.dto.request.branch.GeneratePickupSlotsRequest;
import com.erp.core.dto.request.branch.UpdatePickupTimeSlotRequest;
import com.erp.core.dto.response.branch.PickupTimeSlotResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Nghiệp vụ quản lý khung giờ nhận hàng tại quán (Pickup Time Slots).
 */
public interface PickupTimeSlotService {

    /**
     * Lấy danh sách khung giờ pickup của chi nhánh theo một ngày chỉ định (mặc định hôm nay).
     * @param branchId ID chi nhánh
     * @param date Ngày cần xem (nếu null, lấy ngày hiện tại theo timezone của chi nhánh)
     * @param enforceScope Nếu true, kiểm tra quyền truy cập qua DataScopeHelper (dùng cho API nội bộ)
     */
    List<PickupTimeSlotResponse> getSlots(UUID branchId, LocalDate date, boolean enforceScope);

    /**
     * Tạo mới một khung giờ pickup.
     */
    PickupTimeSlotResponse createSlot(UUID branchId, CreatePickupTimeSlotRequest request);

    /**
     * Cập nhật một khung giờ pickup.
     */
    PickupTimeSlotResponse updateSlot(UUID branchId, UUID slotId, UpdatePickupTimeSlotRequest request);

    /**
     * Xóa một khung giờ pickup (hoặc vô hiệu hóa nếu đã có đơn hàng liên kết).
     */
    void deleteSlot(UUID branchId, UUID slotId);

    /**
     * Tự động sinh hàng loạt khung giờ pickup theo bước nhảy thời gian.
     */
    List<PickupTimeSlotResponse> generateSlots(UUID branchId, GeneratePickupSlotsRequest request);
}
