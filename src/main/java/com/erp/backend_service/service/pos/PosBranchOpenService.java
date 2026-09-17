package com.erp.backend_service.service.pos;

import com.erp.backend_service.repository.BranchHoursRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.core.domain.BranchHours;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Giờ mở cửa chi nhánh theo timezone của chi nhánh.
 * Không cấu hình giờ -> coi như mở (fail-open) để không chặn bán;
 * ngày is_closed hoặc ngoài khung giờ -> đóng, đơn ở PENDING chờ staff.
 * Khung qua đêm (đóng <= mở, vd 22:00-02:00) được tính đúng.
 */
@Service
public class PosBranchOpenService {

    private final BranchRepository branchRepository;
    private final BranchHoursRepository hoursRepository;

    public PosBranchOpenService(BranchRepository branchRepository, BranchHoursRepository hoursRepository) {
        this.branchRepository = branchRepository;
        this.hoursRepository = hoursRepository;
    }

    @Transactional(readOnly = true)
    public boolean isOpenNow(UUID branchId) {
        String tz = branchRepository.findById(branchId)
            .map(b -> b.getTimezone() != null ? b.getTimezone() : "Asia/Ho_Chi_Minh")
            .orElse("Asia/Ho_Chi_Minh");
        ZoneId zone;
        try {
            zone = ZoneId.of(tz);
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Ho_Chi_Minh");
        }
        return isOpenAt(branchId, ZonedDateTime.now(zone));
    }

    /**
     * Kiểm tra trạng thái mở cửa tại một thời điểm cụ thể theo múi giờ chi nhánh.
     * Hỗ trợ ca thường trong ngày và ca qua đêm kéo dài từ tối hôm qua sang sáng sớm hôm nay.
     */
    @Transactional(readOnly = true)
    public boolean isOpenAt(UUID branchId, ZonedDateTime targetTime) {
        int todayDayOfWeek = targetTime.getDayOfWeek().getValue(); // MONDAY=1..SUNDAY=7
        LocalTime currentTime = targetTime.toLocalTime();

        // 1. Kiểm tra cấu hình của ngày hôm nay
        Optional<BranchHours> todayOpt = hoursRepository.findByBranchIdAndDayOfWeekAndStatus(
                branchId, todayDayOfWeek, "ACTIVE");
        if (todayOpt.isPresent()) {
            BranchHours h = todayOpt.get();
            if (!h.isClosed() && h.getOpenTime() != null && h.getCloseTime() != null) {
                LocalTime open = h.getOpenTime();
                LocalTime close = h.getCloseTime();
                if (close.isAfter(open)) {
                    // Ca thường trong ngày (vd: 07:00 - 22:00)
                    if (!currentTime.isBefore(open) && currentTime.isBefore(close)) {
                        return true;
                    }
                } else {
                    // Ca qua đêm bắt đầu từ ngày hôm nay (vd: 20:00 hôm nay - 02:00 sáng mai)
                    if (!currentTime.isBefore(open)) {
                        return true;
                    }
                }
            }
        }

        // 2. Kiểm tra ca qua đêm bắt đầu từ HÔM QUA kéo dài sang sáng sớm hôm nay
        int yesterdayDayOfWeek = todayDayOfWeek == 1 ? 7 : todayDayOfWeek - 1;
        Optional<BranchHours> yesterdayOpt = hoursRepository.findByBranchIdAndDayOfWeekAndStatus(
                branchId, yesterdayDayOfWeek, "ACTIVE");
        if (yesterdayOpt.isPresent()) {
            BranchHours y = yesterdayOpt.get();
            if (!y.isClosed() && y.getOpenTime() != null && y.getCloseTime() != null) {
                LocalTime open = y.getOpenTime();
                LocalTime close = y.getCloseTime();
                // Nếu ca hôm qua là ca qua đêm (close <= open) và hiện tại chưa tới close_time của hôm qua
                if (!close.isAfter(open) && currentTime.isBefore(close)) {
                    return true;
                }
            }
        }

        // Không cấu hình giờ ở cả hôm nay và hôm qua -> coi như mở (fail-open)
        if (todayOpt.isEmpty() && yesterdayOpt.isEmpty()) {
            return true;
        }

        return false;
    }
}
