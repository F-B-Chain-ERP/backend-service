package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ShiftAssignmentMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.ShiftAssignmentRepository;
import com.erp.backend_service.repository.ShiftRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.ShiftAssignmentService;
import com.erp.core.domain.Account;
import com.erp.core.domain.Shift;
import com.erp.core.domain.ShiftAssignment;
import com.erp.core.dto.request.store.BulkAssignShiftRequest;
import com.erp.core.dto.request.store.CreateShiftAssignmentRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.ShiftAssignmentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ShiftAssignmentServiceImpl implements ShiftAssignmentService {

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftRepository shiftRepository;
    private final AccountRepository accountRepository;
    private final BranchRepository branchRepository;
    private final ShiftAssignmentMapper shiftAssignmentMapper;
    private final DataScopeHelper dataScopeHelper;

    private static final Logger log = LoggerFactory.getLogger(ShiftAssignmentServiceImpl.class);

    public ShiftAssignmentServiceImpl(ShiftAssignmentRepository shiftAssignmentRepository,
                                      ShiftRepository shiftRepository,
                                      AccountRepository accountRepository,
                                      BranchRepository branchRepository,
                                      ShiftAssignmentMapper shiftAssignmentMapper,
                                      DataScopeHelper dataScopeHelper) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.shiftRepository = shiftRepository;
        this.accountRepository = accountRepository;
        this.branchRepository = branchRepository;
        this.shiftAssignmentMapper = shiftAssignmentMapper;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Override
    public ShiftAssignmentResponse assignShift(CreateShiftAssignmentRequest request) {
        log.info("Assign shift: branchId={}, shiftId={}, accountId={}, workDate={}",
                request.branchId(), request.shiftId(), request.accountId(), request.workDate());
        dataScopeHelper.enforceBranchAccess(request.branchId());

        if (!branchRepository.existsById(request.branchId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Shift shift = shiftRepository.findByIdAndBranchId(request.shiftId(), request.branchId())
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_SHIFT_NOT_FOUND));

        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (shiftAssignmentRepository.existsByShiftIdAndAccountIdAndWorkDate(request.shiftId(), request.accountId(), request.workDate())) {
            throw new BaseException(ErrorCode.STORE_409_ASSIGNMENT_EXISTS);
        }

        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setShiftId(request.shiftId());
        assignment.setBranchId(request.branchId());
        assignment.setAccountId(request.accountId());
        assignment.setWorkDate(request.workDate());
        assignment.setStatus("SCHEDULED");
        assignment.setNote(request.note());

        ShiftAssignment saved = shiftAssignmentRepository.save(assignment);
        return shiftAssignmentMapper.toResponse(saved, shift, account);
    }

    @Override
    public List<ShiftAssignmentResponse> bulkAssignShifts(BulkAssignShiftRequest request) {
        log.info("Bulk assign shifts: branchId={}, count={}", request.branchId(),
                request.assignments() != null ? request.assignments().size() : 0);
        dataScopeHelper.enforceBranchAccess(request.branchId());

        if (!branchRepository.existsById(request.branchId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        List<ShiftAssignment> toSave = new ArrayList<>();
        for (BulkAssignShiftRequest.AssignmentItem item : request.assignments()) {
            if (shiftAssignmentRepository.existsByShiftIdAndAccountIdAndWorkDate(item.shiftId(), item.accountId(), item.workDate())) {
                continue; // Bỏ qua bản ghi đã trùng hoặc ném exception nếu cần
            }

            ShiftAssignment assignment = new ShiftAssignment();
            assignment.setShiftId(item.shiftId());
            assignment.setBranchId(request.branchId());
            assignment.setAccountId(item.accountId());
            assignment.setWorkDate(item.workDate());
            assignment.setStatus("SCHEDULED");
            assignment.setNote(item.note());
            toSave.add(assignment);
        }

        List<ShiftAssignment> saved = shiftAssignmentRepository.saveAll(toSave);

        // Batch load shifts và accounts để map kết quả
        var shiftIds = saved.stream().map(ShiftAssignment::getShiftId).distinct().toList();
        var accountIds = saved.stream().map(ShiftAssignment::getAccountId).distinct().toList();

        Map<UUID, Shift> shiftMap = shiftRepository.findAllById(shiftIds).stream()
                .collect(Collectors.toMap(Shift::getId, Function.identity()));
        Map<UUID, Account> accountMap = accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        return saved.stream()
                .map(a -> shiftAssignmentMapper.toResponse(a, shiftMap.get(a.getShiftId()), accountMap.get(a.getAccountId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftAssignmentResponse getAssignmentById(UUID id) {
        log.info("Get assignment id={}", id);
        ShiftAssignment assignment = shiftAssignmentRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_ASSIGNMENT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(assignment.getBranchId());

        Shift shift = shiftRepository.findById(assignment.getShiftId()).orElse(null);
        Account account = accountRepository.findById(assignment.getAccountId()).orElse(null);

        return shiftAssignmentMapper.toResponse(assignment, shift, account);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShiftAssignmentResponse> searchAssignments(UUID branchId,
                                                                  LocalDate startDate,
                                                                  LocalDate endDate,
                                                                  UUID accountId,
                                                                  String status,
                                                                  Pageable pageable) {
        log.info("Search assignments: branchId={}, startDate={}, endDate={}, accountId={}, status={}, page={}, size={}",
                branchId, startDate, endDate, accountId, status, pageable.getPageNumber(), pageable.getPageSize());
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(branchId);

        Specification<ShiftAssignment> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (effectiveBranchId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("branchId"), effectiveBranchId));
            }
            if (startDate != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("workDate"), startDate));
            }
            if (endDate != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("workDate"), endDate));
            }
            if (accountId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("accountId"), accountId));
            }
            if (status != null && !status.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            return predicates;
        };

        Page<ShiftAssignment> page = shiftAssignmentRepository.findAll(spec, pageable);
        var assignments = page.getContent();

        var shiftIds = assignments.stream().map(ShiftAssignment::getShiftId).distinct().toList();
        var accountIds = assignments.stream().map(ShiftAssignment::getAccountId).distinct().toList();

        Map<UUID, Shift> shiftMap = shiftRepository.findAllById(shiftIds).stream()
                .collect(Collectors.toMap(Shift::getId, Function.identity()));
        Map<UUID, Account> accountMap = accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<ShiftAssignmentResponse> items = assignments.stream()
                .map(a -> shiftAssignmentMapper.toResponse(a, shiftMap.get(a.getShiftId()), accountMap.get(a.getAccountId())))
                .toList();

        return new PageResponse<>(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), items);
    }

    @Override
    public void cancelAssignment(UUID id, String reason) {
        log.info("Cancel assignment id={}, reason={}", id, reason);
        ShiftAssignment assignment = shiftAssignmentRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_ASSIGNMENT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(assignment.getBranchId());

        if (!"SCHEDULED".equalsIgnoreCase(assignment.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        assignment.setStatus("CANCELLED");
        if (reason != null && !reason.isBlank()) {
            assignment.setNote(assignment.getNote() != null ? assignment.getNote() + " | Lý do hủy: " + reason : "Lý do hủy: " + reason);
        }
        shiftAssignmentRepository.save(assignment);
    }
}
