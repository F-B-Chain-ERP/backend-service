package com.erp.backend_service.service;

import com.erp.core.dto.request.fin.RecalculateFinancialSummaryRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.fin.FinancialSummaryResponse;
import com.erp.core.dto.response.fin.FinancialSummarySourceResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service tổng hợp tài chính ngày (S5-04): xem danh sách/chi tiết, đối soát dữ liệu nguồn,
 * tính lại toàn bộ và chốt kỳ.
 */
public interface FinancialSummaryService {

    PageResponse<FinancialSummaryResponse> list(UUID branchId, LocalDate fromDate, LocalDate toDate,
                                                 String status, int page, int size, String sortBy, String sortDir);

    FinancialSummaryResponse get(UUID id);

    FinancialSummarySourceResponse<?> getSources(UUID id, String source, int page, int size);

    FinancialSummaryResponse recalculate(RecalculateFinancialSummaryRequest request);

    FinancialSummaryResponse finalize(UUID id);
}