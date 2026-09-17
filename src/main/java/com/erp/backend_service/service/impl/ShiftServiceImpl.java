package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ShiftMapper;
import com.erp.backend_service.repository.BranchRepository;
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

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ShiftServiceImpl implements ShiftService {

    private final ShiftRepository shiftRepository;
    private final BranchRepository branchRepository;
    private final ShiftMapper shiftMapper;
    private final DataScopeHelper dataScopeHelper;

    public ShiftServiceImpl(ShiftRepository shiftRepository,
                            BranchRepository branchRepository,
                            ShiftMapper shiftMapper,
                            DataScopeHelper dataScopeHelper) {
        this.shiftRepository = shiftRepository;
        this.branchRepository = branchRepository;
        this.shiftMapper = shiftMapper;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Override
    public ShiftResponse createShift(CreateShiftRequest request) {
        dataScopeHelper.enforceBranchAccess(request.branchId());

        if (!branchRepository.existsById(request.branchId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
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

        if (request.startTime().equals(request.endTime())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_HOURS);
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
    @Transactional(readOnly = true)
    public List<ShiftResponse> getShiftsByBranch(UUID branchId, String status) {
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(branchId);
        List<Shift> shifts;
        if (status != null && !status.isBlank()) {
            shifts = shiftRepository.findByBranchIdAndStatus(effectiveBranchId, status.trim().toUpperCase());
        } else {
            shifts = shiftRepository.findByBranchId(effectiveBranchId);
        }
        return shifts.stream().map(shiftMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShiftResponse> searchShifts(UUID branchId, String status, Pageable pageable) {
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(branchId);

        Specification<Shift> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (effectiveBranchId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("branchId"), effectiveBranchId));
            }
            if (status != null && !status.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status.trim().toUpperCase()));
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
        shiftRepository.delete(shift);
    }
}
