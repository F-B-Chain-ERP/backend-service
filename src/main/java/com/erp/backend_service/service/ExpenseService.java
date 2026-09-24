package com.erp.backend_service.service;

import com.erp.core.dto.request.fin.CreateExpenseRequest;
import com.erp.core.dto.request.fin.UpdateExpenseRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.fin.ExpenseResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Nghiệp vụ quản lý chi phí vận hành (Expense).
 */
public interface ExpenseService {

    PageResponse<ExpenseResponse> list(int page, int size, String search, UUID branchId,
                                       String category, String status,
                                       LocalDate dateFrom, LocalDate dateTo,
                                       String sortBy, String sortDir);

    ExpenseResponse get(UUID id);

    ExpenseResponse create(CreateExpenseRequest request);

    ExpenseResponse update(UUID id, UpdateExpenseRequest request);

    void delete(UUID id);
}