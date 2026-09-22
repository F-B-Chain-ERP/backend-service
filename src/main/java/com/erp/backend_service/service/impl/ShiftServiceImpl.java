package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ShiftMapper;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.ShiftAssignmentRepository;
import com.erp.backend_service.repository.ShiftRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.ShiftService;
import com.erp.core.domain.Shift;
import com.erp.core.dto.request.store.CreateShiftRequest;
import com.erp.core.dto.request.store.UpdateShiftRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.ShiftResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ShiftServiceImpl implements ShiftService {

    private final ShiftRepository shiftRepository;
    private final BranchRepository branchRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftMapper shiftMapper;
    private final DataScopeHelper dataScopeHelper;

    private static final String SYSTEM_SHIFT_A = "CA_A";
    private static final String SYSTEM_SHIFT_B = "CA_B";

    public ShiftServiceImpl(ShiftRepository shiftRepository,
                            BranchRepository branchRepository,
                            ShiftAssignmentRepository shiftAssignmentRepository,
                            ShiftMapper shiftMapper,
                            DataScopeHelper dataScopeHelper) {
        this.shiftRepository = shiftRepository;
        this.branchRepository = branchRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.shiftMapper = shiftMapper;
        this.dataScopeHelper = dataScopeHelper;
    }

    private static boolean isSystemShiftCode(String code) {
        if (code == null) {
            return false;
        }
        String normalized = code.trim().toUpperCase();
        return SYSTEM_SHIFT_A.equals(normalized) || SYSTEM_SHIFT_B.equals(normalized);
    }

    private void ensureSystemShifts(UUID branchId) {
        if (branchId == null || !branchRepository.existsById(branchId)) {
            return;
        }
        ensureOneSystemShift(branchId, SYSTEM_SHIFT_A, "Ca A", LocalTime.of(6, 30), LocalTime.of(15, 0));
        ensureOneSystemShift(branchId, SYSTEM_SHIFT_B, "Ca B", LocalTime.of(15, 0), LocalTime.of(23, 0));
    }

    private void ensureOneSystemShift(UUID branchId, String code, String name, LocalTime start, LocalTime end) {
        if (shiftRepository.existsByBranchIdAndShiftCode(branchId, code)) {
            return;
        }
        Shift shift = new Shift();
        shift.setBranchId(branchId);
        shift.setShiftCode(code);
        shift.setShiftName(name);
        shift.setStartTime(start);
        shift.setEndTime(end);
        shift.setStatus("ACTIVE");
        shiftRepository.save(shift);
    }

    @Override
    public ShiftResponse createShift(CreateShiftRequest request) {
        dataScopeHelper.enforceBranchAccess(request.branchId());

        if (!branchRepository.existsById(request.branchId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        String normalizedCode = request.shiftCode() != null ? request.shiftCode().trim().toUpperCase() : "";
        if (isSystemShiftCode(normalizedCode)) {
            throw new BaseException(ErrorCode.STORE_409_SHIFT_CODE_EXISTS);
        }

        if (request.startTime().equals(request.endTime())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_HOURS);
        }

        if (shiftRepository.existsByBranchIdAndShiftCode(request.branchId(), request.shiftCode().trim().toUpperCase())) {
            throw new BaseException(ErrorCode.STORE_409_SHIFT_CODE_EXISTS);
        }

        Shift shift = new Shift();
        shift.setBranchId(request.branchId());
        shift.setShiftCode(request.shiftCode().trim().toUpperCase());
        shift.setShiftName(request.shiftName().trim());
        shift.setStartTime(request.startTime());
        shift.setEndTime(request.endTime());
        shift.setStatus(request.status() != null && !request.status().isBlank() ? request.status() : "ACTIVE");

        Shift saved = shiftRepository.save(shift);
        return shiftMapper.toResponse(saved);
    }

    @Override
    public ShiftResponse updateShift(UUID id, UpdateShiftRequest request) {
        Shift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_SHIFT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(shift.getBranchId());

        if (isSystemShiftCode(shift.getShiftCode())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        if (request.startTime().equals(request.endTime())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_HOURS);
        }

        boolean toInactive = request.status() != null && !request.status().isBlank()
                && !"ACTIVE".equalsIgnoreCase(request.status().trim())
                && "ACTIVE".equalsIgnoreCase(shift.getStatus());
        if (toInactive && shiftAssignmentRepository.existsByShiftIdAndStatusIn(
                shift.getId(), List.of("SCHEDULED", "CHECKED_IN"))) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        shift.setShiftName(request.shiftName().trim());
        shift.setStartTime(request.startTime());
        shift.setEndTime(request.endTime());
        if (request.status() != null && !request.status().isBlank()) {
            shift.setStatus(request.status());
        }

        Shift updated = shiftRepository.save(shift);
        return shiftMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftResponse getShiftById(UUID id) {
        Shift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_SHIFT_NOT_FOUND));
        dataScopeHelper.enforceBranchAccess(shift.getBranchId());
        return shiftMapper.toResponse(shift);
    }

    @Override
    @Transactional
    public List<ShiftResponse> getShiftsByBranch(UUID branchId, String status) {
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(branchId);
        if (effectiveBranchId != null) {
            ensureSystemShifts(effectiveBranchId);
        }
        List<Shift> shifts;
        if (status != null && !status.isBlank()) {
            shifts = shiftRepository.findByBranchIdAndStatus(effectiveBranchId, status.trim().toUpperCase());
        } else {
            shifts = shiftRepository.findByBranchId(effectiveBranchId);
        }
        return shifts.stream().map(shiftMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public PageResponse<ShiftResponse> searchShifts(UUID branchId, String status, String query, Pageable pageable) {
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(branchId);
        if (effectiveBranchId != null) {
            ensureSystemShifts(effectiveBranchId);
        }

        Specification<Shift> spec = (root, queryBuilder, cb) -> {
            var predicates = cb.conjunction();
            if (effectiveBranchId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("branchId"), effectiveBranchId));
            }
            if (status != null && !status.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            if (query != null && !query.isBlank()) {
                String keyword = "%" + query.trim().toLowerCase() + "%";
                predicates = cb.and(predicates, cb.or(
                        cb.like(cb.lower(root.get("shiftCode")), keyword),
                        cb.like(cb.lower(root.get("shiftName")), keyword)));
            }
            return predicates;
        };

        Page<Shift> page = shiftRepository.findAll(spec, pageable);
        List<ShiftResponse> items = page.getContent().stream().map(shiftMapper::toResponse).toList();
        return new PageResponse<>(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), items);
    }

    @Override
    public void deleteShift(UUID id) {
        Shift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_SHIFT_NOT_FOUND));
        dataScopeHelper.enforceBranchAccess(shift.getBranchId());
        if (isSystemShiftCode(shift.getShiftCode())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }
        if (shiftAssignmentRepository.existsByShiftIdAndStatusIn(
                shift.getId(), List.of("SCHEDULED", "CHECKED_IN", "CHECKED_OUT"))) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }
        shiftRepository.delete(shift);
    }
}
