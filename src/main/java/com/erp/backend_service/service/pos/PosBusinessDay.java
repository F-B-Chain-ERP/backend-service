package com.erp.backend_service.service.pos;

import com.erp.backend_service.repository.BranchRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Ngày kinh doanh theo timezone của chi nhánh (A3).
 * Kho daily-stock chốt theo ngày này, không phải LocalDate.now() của server.
 */
@Component
public class PosBusinessDay {

    private final BranchRepository branchRepository;

    public PosBusinessDay(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    public LocalDate today(UUID branchId) {
        String tz = branchRepository.findById(branchId)
            .map(b -> b.getTimezone() != null ? b.getTimezone() : "Asia/Ho_Chi_Minh")
            .orElse("Asia/Ho_Chi_Minh");
        try {
            return LocalDate.now(ZoneId.of(tz));
        } catch (Exception e) {
            return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }
}
