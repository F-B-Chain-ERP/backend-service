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
        ZonedDateTime now = ZonedDateTime.now(zone);
        int dayOfWeek = now.getDayOfWeek().getValue(); // MONDAY=1..SUNDAY=7, khớp ck_branch_hours_day
        Optional<BranchHours> hours = hoursRepository.findByBranchIdAndDayOfWeekAndStatus(branchId, dayOfWeek,
            "ACTIVE");
        if (hours.isEmpty()) {
            return true;
        }
        BranchHours h = hours.get();
        if (h.isClosed()) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        LocalTime open = h.getOpenTime();
        LocalTime close = h.getCloseTime();
        if (open == null || close == null) {
            return true;
        }
        if (!close.isAfter(open)) {
            return !time.isBefore(open) || time.isBefore(close);
        }
        return !time.isBefore(open) && time.isBefore(close);
    }
}
