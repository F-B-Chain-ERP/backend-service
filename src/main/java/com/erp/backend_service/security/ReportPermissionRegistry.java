package com.erp.backend_service.security;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.core.constants.ReportPermissions;
import com.erp.core.enums.ReportType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Registry quản lý và kiểm tra quyền tập trung cho từng loại báo cáo (Report Type).
 */
@Component
public class ReportPermissionRegistry {

    private static final Map<ReportType, String> EXPORT_PERMISSIONS = Map.ofEntries(
            Map.entry(ReportType.POS_ORDER_LIST, ReportPermissions.POS_ORDER_LIST_EXPORT),
            Map.entry(ReportType.POS_ORDER_EXPORT, ReportPermissions.POS_ORDER_LIST_EXPORT),
            Map.entry(ReportType.POS_SALES_SUMMARY, ReportPermissions.POS_SALES_SUMMARY_EXPORT),
            Map.entry(ReportType.STORE_SHIFT_HANDOVER, ReportPermissions.STORE_SHIFT_EXPORT),
            Map.entry(ReportType.STORE_SHIFT_REPORT, ReportPermissions.STORE_SHIFT_EXPORT),
            Map.entry(ReportType.STORE_SHIFT_LIST, ReportPermissions.STORE_SHIFT_EXPORT),
            Map.entry(ReportType.STORE_DAILY_CLOSING, ReportPermissions.STORE_DAILY_EXPORT),
            Map.entry(ReportType.STORE_DAILY_REPORT, ReportPermissions.STORE_DAILY_EXPORT),
            Map.entry(ReportType.STORE_DAILY_LIST, ReportPermissions.STORE_DAILY_EXPORT),
            Map.entry(ReportType.FIN_SUMMARY_EXPORT, ReportPermissions.FIN_REPORT_EXPORT),
            Map.entry(ReportType.INV_STOCK_BALANCE, ReportPermissions.INV_REPORT_EXPORT),
            Map.entry(ReportType.PROC_PO_EXPORT, ReportPermissions.PROC_REPORT_EXPORT)
    );

    public String getRequiredPermission(ReportType type) {
        if (type == null) {
            return null;
        }
        return EXPORT_PERMISSIONS.get(type);
    }

    public void checkPermission(ReportType type) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        checkPermission(type, auth);
    }

    public void checkPermission(ReportType type, Authentication auth) {
        if (type == null) {
            return;
        }
        String required = getRequiredPermission(type);
        if (required == null) {
            return; // Loại báo cáo không ràng buộc quyền đặc thù
        }
        if (auth == null || auth.getAuthorities() == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED);
        }
        boolean hasPermission = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase(required)
                        || a.getAuthority().equalsIgnoreCase("ROLE_ADMIN")
                        || a.getAuthority().equalsIgnoreCase("ADMIN")
                        || a.getAuthority().equalsIgnoreCase("ROOT"));
        if (!hasPermission) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xuất báo cáo: " + type.name());
        }
    }
}
