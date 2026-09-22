package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.BranchHoursRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.BranchHoursService;
import com.erp.core.domain.BranchHours;
import com.erp.core.dto.request.branch.BatchUpdateBranchHoursRequest;
import com.erp.core.dto.request.branch.UpdateBranchHoursItemRequest;
import com.erp.core.dto.response.branch.BranchHoursResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.*;

@Service
public class BranchHoursServiceImpl implements BranchHoursService {

    private static final Logger log = LoggerFactory.getLogger(BranchHoursServiceImpl.class);
    private static final LocalTime DEFAULT_OPEN_TIME = LocalTime.of(7, 0);
    private static final LocalTime DEFAULT_CLOSE_TIME = LocalTime.of(22, 0);

    private final BranchHoursRepository hoursRepository;
    private final BranchRepository branchRepository;
    private final DataScopeHelper dataScopeHelper;

    public BranchHoursServiceImpl(BranchHoursRepository hoursRepository,
                                  BranchRepository branchRepository,
                                  DataScopeHelper dataScopeHelper) {
        this.hoursRepository = hoursRepository;
        this.branchRepository = branchRepository;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Override
    @Transactional
    public List<BranchHoursResponse> getHours(UUID branchId) {
        log.info("Get hours: branchId={}", branchId);
        validateBranchAndScope(branchId);

        List<BranchHours> hoursList = hoursRepository.findByBranchIdOrderByDayOfWeekAsc(branchId);
        if (hoursList.size() < 7) {
            initDefaultHours(branchId);
            hoursList = hoursRepository.findByBranchIdOrderByDayOfWeekAsc(branchId);
        }

        return hoursList.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<BranchHoursResponse> updateHours(UUID branchId, BatchUpdateBranchHoursRequest request) {
        validateBranchAndScope(branchId);

        if (request == null || request.hours() == null || request.hours().isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Danh sách giờ hoạt động không được để trống.");
        }

        Map<Integer, BranchHours> existingMap = new HashMap<>();
        for (BranchHours bh : hoursRepository.findByBranchIdOrderByDayOfWeekAsc(branchId)) {
            existingMap.put(bh.getDayOfWeek(), bh);
        }

        List<BranchHours> toSave = new ArrayList<>();

        for (UpdateBranchHoursItemRequest item : request.hours()) {
            int day = item.dayOfWeek();
            boolean isClosed = item.isClosed();
            LocalTime open = item.openTime();
            LocalTime close = item.closeTime();

            // Ràng buộc BR-ORG-03: Nếu không đóng cửa, open_time và close_time không được null và không được trùng nhau
            if (!isClosed) {
                if (open == null || close == null) {
                    throw new BaseException(ErrorCode.BRANCH_HOURS_400_INVALID_TIMES,
                            "Giờ mở cửa và đóng cửa là bắt buộc khi không đánh dấu đóng cửa cả ngày.");
                }
                if (open.equals(close)) {
                    throw new BaseException(ErrorCode.BRANCH_HOURS_400_INVALID_TIMES,
                            "Giờ mở cửa và đóng cửa không được trùng nhau. Nếu nghỉ bán, vui lòng chọn đóng cửa cả ngày.");
                }
            } else {
                // Nếu đánh dấu đóng cửa cả ngày mà không truyền giờ, gán giá trị mặc định để thỏa mãn NOT NULL của CSDL
                if (open == null) {
                    open = DEFAULT_OPEN_TIME;
                }
                if (close == null) {
                    close = DEFAULT_CLOSE_TIME;
                }
            }

            BranchHours record = existingMap.get(day);
            if (record == null) {
                record = new BranchHours();
                record.setBranchId(branchId);
                record.setDayOfWeek(day);
            }

            record.setOpenTime(open);
            record.setCloseTime(close);
            record.setClosed(isClosed);
            record.setStatus(item.status() != null && !item.status().isBlank() ? item.status() : "ACTIVE");

            toSave.add(record);
        }

        hoursRepository.saveAll(toSave);

        return hoursRepository.findByBranchIdOrderByDayOfWeekAsc(branchId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void initDefaultHours(UUID branchId) {
        if (!branchRepository.existsById(branchId)) {
            throw new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND);
        }

        Map<Integer, BranchHours> existing = new HashMap<>();
        for (BranchHours bh : hoursRepository.findByBranchIdOrderByDayOfWeekAsc(branchId)) {
            existing.put(bh.getDayOfWeek(), bh);
        }

        List<BranchHours> newHours = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            if (!existing.containsKey(day)) {
                BranchHours bh = new BranchHours();
                bh.setBranchId(branchId);
                bh.setDayOfWeek(day);
                bh.setOpenTime(DEFAULT_OPEN_TIME);
                bh.setCloseTime(DEFAULT_CLOSE_TIME);
                bh.setClosed(false);
                bh.setStatus("ACTIVE");
                newHours.add(bh);
            }
        }

        if (!newHours.isEmpty()) {
            hoursRepository.saveAll(newHours);
        }
    }

    private void validateBranchAndScope(UUID branchId) {
        if (branchId == null || !branchRepository.existsById(branchId)) {
            throw new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND);
        }
        dataScopeHelper.enforceBranchAccess(branchId);
    }

    private BranchHoursResponse toResponse(BranchHours entity) {
        String dayName = getDayOfWeekName(entity.getDayOfWeek());
        boolean isOvernight = !entity.isClosed()
                && entity.getOpenTime() != null
                && entity.getCloseTime() != null
                && !entity.getCloseTime().isAfter(entity.getOpenTime());

        return new BranchHoursResponse(
                entity.getId() != null ? entity.getId().toString() : null,
                entity.getBranchId(),
                entity.getDayOfWeek(),
                dayName,
                entity.getOpenTime(),
                entity.getCloseTime(),
                entity.isClosed(),
                isOvernight,
                entity.getStatus()
        );
    }

    private String getDayOfWeekName(int dayOfWeek) {
        return switch (dayOfWeek) {
            case 1 -> "Thứ Hai";
            case 2 -> "Thứ Ba";
            case 3 -> "Thứ Tư";
            case 4 -> "Thứ Năm";
            case 5 -> "Thứ Sáu";
            case 6 -> "Thứ Bảy";
            case 7 -> "Chủ Nhật";
            default -> "Không xác định";
        };
    }
}
