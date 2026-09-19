package com.erp.backend_service.export;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.core.enums.ExportFormat;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Factory quản lý và cung cấp ExportStrategy tương ứng với định dạng xuất (EXCEL, PDF).
 */
@Component
public class ExportStrategyFactory {

    private final Map<ExportFormat, ExportStrategy> strategies = new EnumMap<>(ExportFormat.class);

    public ExportStrategyFactory(List<ExportStrategy> strategyList) {
        for (ExportStrategy strategy : strategyList) {
            strategies.put(strategy.getSupportedFormat(), strategy);
        }
    }

    /**
     * Lấy chiến lược xuất tương ứng với định dạng yêu cầu.
     *
     * @param format Định dạng xuất (EXCEL hoặc PDF)
     * @return Chiến lược xử lý tương ứng
     */
    public ExportStrategy getStrategy(ExportFormat format) {
        if (format == null) {
            format = ExportFormat.EXCEL;
        }
        ExportStrategy strategy = strategies.get(format);
        if (strategy == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Định dạng xuất không được hỗ trợ: " + format);
        }
        return strategy;
    }
}
