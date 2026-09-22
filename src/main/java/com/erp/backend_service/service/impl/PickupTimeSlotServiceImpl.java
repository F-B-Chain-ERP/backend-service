package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.OrderRepository;
import com.erp.backend_service.repository.PickupTimeSlotRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.PickupTimeSlotService;
import com.erp.core.domain.Branch;
import com.erp.core.domain.PickupTimeSlot;
import com.erp.core.dto.request.branch.CreatePickupTimeSlotRequest;
import com.erp.core.dto.request.branch.GeneratePickupSlotsRequest;
import com.erp.core.dto.request.branch.UpdatePickupTimeSlotRequest;
import com.erp.core.dto.response.branch.PickupTimeSlotResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PickupTimeSlotServiceImpl implements PickupTimeSlotService {

    private final PickupTimeSlotRepository slotRepository;
    private final BranchRepository branchRepository;
    private final OrderRepository orderRepository;
    private final DataScopeHelper dataScopeHelper;

    private static final Logger log = LoggerFactory.getLogger(PickupTimeSlotServiceImpl.class);

    public PickupTimeSlotServiceImpl(PickupTimeSlotRepository slotRepository,
                                     BranchRepository branchRepository,
                                     OrderRepository orderRepository,
                                     DataScopeHelper dataScopeHelper) {
        this.slotRepository = slotRepository;
        this.branchRepository = branchRepository;
        this.orderRepository = orderRepository;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PickupTimeSlotResponse> getSlots(UUID branchId, LocalDate date, boolean enforceScope) {
        log.info("Get pickup time slots: branchId={}, date={}, enforceScope={}", branchId, date, enforceScope);
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND));

        if (enforceScope) {
            dataScopeHelper.enforceBranchAccess(branchId);
        }

        ZoneId zone = resolveZone(branch.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate targetDate = date != null ? date : now.toLocalDate();

        Instant startOfDay = targetDate.atStartOfDay(zone).toInstant();
        Instant endOfDay = targetDate.plusDays(1).atStartOfDay(zone).toInstant();

        List<PickupTimeSlot> slots = slotRepository.findByBranchIdOrderByStartTimeAsc(branchId);
        if (slots.isEmpty()) {
            return List.of();
        }

        List<UUID> slotIds = slots.stream().map(PickupTimeSlot::getId).toList();
        List<Object[]> countRows = orderRepository.countActiveOrdersBySlotIdsOnDate(
                branchId, slotIds, startOfDay, endOfDay);

        Map<UUID, Long> orderCountMap = new HashMap<>();
        for (Object[] row : countRows) {
            if (row != null && row.length >= 2 && row[0] instanceof UUID sId && row[1] instanceof Number cnt) {
                orderCountMap.put(sId, cnt.longValue());
            }
        }

        boolean isToday = targetDate.isEqual(now.toLocalDate());
        LocalTime currentTime = now.toLocalTime();

        return slots.stream().map(slot -> {
            long currentOrders = orderCountMap.getOrDefault(slot.getId(), 0L);

            boolean isAvailable = "ACTIVE".equalsIgnoreCase(slot.getStatus());
            if (slot.getMaxOrders() != null && currentOrders >= slot.getMaxOrders()) {
                isAvailable = false;
            }
            // Nếu là ngày hôm nay mà khung giờ đã qua thì không còn khả dụng
            if (isToday && slot.getEndTime() != null && currentTime.isAfter(slot.getEndTime())) {
                isAvailable = false;
            }

            return new PickupTimeSlotResponse(
                    slot.getId() != null ? slot.getId().toString() : null,
                    slot.getBranchId(),
                    slot.getSlotCode(),
                    slot.getStartTime(),
                    slot.getEndTime(),
                    slot.getMaxOrders(),
                    currentOrders,
                    isAvailable,
                    slot.getStatus()
            );
        }).toList();
    }

    @Override
    @Transactional
    public PickupTimeSlotResponse createSlot(UUID branchId, CreatePickupTimeSlotRequest request) {
        log.info("Create pickup time slot: branchId={}, slotCode={}", branchId, request.slotCode());
        validateBranchAndScope(branchId);
        validateSlotTimes(request.startTime(), request.endTime());

        if (slotRepository.existsByBranchIdAndSlotCode(branchId, request.slotCode().trim())) {
            throw new BaseException(ErrorCode.PICKUP_SLOT_409_CODE_EXISTS);
        }

        PickupTimeSlot slot = new PickupTimeSlot();
        slot.setBranchId(branchId);
        slot.setSlotCode(request.slotCode().trim());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setMaxOrders(request.maxOrders());
        slot.setStatus(request.status() != null && !request.status().isBlank() ? request.status() : "ACTIVE");

        PickupTimeSlot saved = slotRepository.save(slot);
        return toResponse(saved, 0L, true);
    }

    @Override
    @Transactional
    public PickupTimeSlotResponse updateSlot(UUID branchId, UUID slotId, UpdatePickupTimeSlotRequest request) {
        log.info("Update pickup time slot: branchId={}, slotId={}", branchId, slotId);
        validateBranchAndScope(branchId);
        validateSlotTimes(request.startTime(), request.endTime());

        PickupTimeSlot slot = slotRepository.findByIdAndBranchId(slotId, branchId)
                .orElseThrow(() -> new BaseException(ErrorCode.PICKUP_SLOT_404_NOT_FOUND));

        if (slotRepository.existsByBranchIdAndSlotCodeAndIdNot(branchId, request.slotCode().trim(), slotId)) {
            throw new BaseException(ErrorCode.PICKUP_SLOT_409_CODE_EXISTS);
        }

        slot.setSlotCode(request.slotCode().trim());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setMaxOrders(request.maxOrders());
        if (request.status() != null && !request.status().isBlank()) {
            slot.setStatus(request.status());
        }

        PickupTimeSlot saved = slotRepository.save(slot);
        return toResponse(saved, 0L, true);
    }

    @Override
    @Transactional
    public void deleteSlot(UUID branchId, UUID slotId) {
        log.info("Delete pickup time slot: branchId={}, slotId={}", branchId, slotId);
        validateBranchAndScope(branchId);

        PickupTimeSlot slot = slotRepository.findByIdAndBranchId(slotId, branchId)
                .orElseThrow(() -> new BaseException(ErrorCode.PICKUP_SLOT_404_NOT_FOUND));

        // Nếu đã có đơn hàng liên kết thì chỉ chuyển INACTIVE để bảo toàn toàn vẹn dữ liệu
        if (orderRepository.existsByPickupTimeSlotId(slotId)) {
            slot.setStatus("INACTIVE");
            slotRepository.save(slot);
        } else {
            slotRepository.delete(slot);
        }
    }

    @Override
    @Transactional
    public List<PickupTimeSlotResponse> generateSlots(UUID branchId, GeneratePickupSlotsRequest request) {
        log.info("Generate pickup time slots: branchId={}, startTime={}, endTime={}, stepMinutes={}, maxOrders={}",
                branchId, request.startTime(), request.endTime(), request.stepMinutes(), request.maxOrders());
        validateBranchAndScope(branchId);

        LocalTime start = request.startTime();
        LocalTime end = request.endTime();
        int step = request.stepMinutes();

        if (start == null || end == null || !start.isBefore(end)) {
            throw new BaseException(ErrorCode.PICKUP_SLOT_400_INVALID_TIME_RANGE,
                    "Giờ bắt đầu phải trước giờ kết thúc.");
        }

        LocalTime cursor = start;
        List<PickupTimeSlot> toSave = new ArrayList<>();

        while (cursor.plusMinutes(step).isBefore(end) || cursor.plusMinutes(step).equals(end)) {
            LocalTime slotStart = cursor;
            LocalTime slotEnd = cursor.plusMinutes(step);

            String code = String.format("SLOT-%02d%02d-%02d%02d",
                    slotStart.getHour(), slotStart.getMinute(),
                    slotEnd.getHour(), slotEnd.getMinute());

            if (!slotRepository.existsByBranchIdAndSlotCode(branchId, code)) {
                PickupTimeSlot slot = new PickupTimeSlot();
                slot.setBranchId(branchId);
                slot.setSlotCode(code);
                slot.setStartTime(slotStart);
                slot.setEndTime(slotEnd);
                slot.setMaxOrders(request.maxOrders());
                slot.setStatus("ACTIVE");
                toSave.add(slot);
            }

            cursor = slotEnd;
        }

        if (!toSave.isEmpty()) {
            slotRepository.saveAll(toSave);
        }

        return getSlots(branchId, null, true);
    }

    private void validateBranchAndScope(UUID branchId) {
        if (branchId == null || !branchRepository.existsById(branchId)) {
            throw new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND);
        }
        dataScopeHelper.enforceBranchAccess(branchId);
    }

    private void validateSlotTimes(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            throw new BaseException(ErrorCode.PICKUP_SLOT_400_INVALID_TIME_RANGE,
                    "Giờ bắt đầu và kết thúc không được để trống.");
        }
        // Ràng buộc BR-ORG-04: start_time < end_time (không áp dụng qua đêm cho pickup slot)
        if (!start.isBefore(end)) {
            throw new BaseException(ErrorCode.PICKUP_SLOT_400_INVALID_TIME_RANGE,
                    "Giờ bắt đầu phải trước giờ kết thúc (khung giờ pickup không áp dụng ca qua đêm).");
        }
        // Thời lượng tối thiểu 15 phút, tối đa 120 phút
        long duration = ChronoUnit.MINUTES.between(start, end);
        if (duration < 15 || duration > 120) {
            throw new BaseException(ErrorCode.PICKUP_SLOT_400_INVALID_TIME_RANGE,
                    "Độ dài mỗi khung giờ pickup phải từ 15 đến 120 phút.");
        }
    }

    private ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception e) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
    }

    private PickupTimeSlotResponse toResponse(PickupTimeSlot entity, long currentOrders, boolean isAvailable) {
        return new PickupTimeSlotResponse(
                entity.getId() != null ? entity.getId().toString() : null,
                entity.getBranchId(),
                entity.getSlotCode(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getMaxOrders(),
                currentOrders,
                isAvailable,
                entity.getStatus()
        );
    }
}
