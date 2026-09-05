package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.UnitMapper;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.UnitRepository;
import com.erp.backend_service.service.UnitService;
import com.erp.core.domain.Unit;
import com.erp.core.dto.request.menu.CreateUnitRequest;
import com.erp.core.dto.request.menu.UpdateUnitRequest;
import com.erp.core.dto.response.menu.UnitResponse;
import com.erp.core.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Xử lý nghiệp vụ đơn vị tính (master dùng chung INV + MENU).
 */
@Service
public class UnitServiceImpl implements UnitService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UnitRepository unitRepository;
    private final MaterialRepository materialRepository;
    private final UnitMapper unitMapper;

    public UnitServiceImpl(UnitRepository unitRepository,
                           MaterialRepository materialRepository,
                           UnitMapper unitMapper) {
        this.unitRepository = unitRepository;
        this.materialRepository = materialRepository;
        this.unitMapper = unitMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UnitResponse> list(int page, int size, String search, String unitType, String status) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending());
        Page<Unit> pageResult = unitRepository.search(
                StringUtils.hasText(search) ? search.trim() : null,
                StringUtils.hasText(unitType) ? unitType.trim().toUpperCase() : null,
                StringUtils.hasText(status) ? status.trim().toUpperCase() : null,
                pageable);
        return new PageResponse<>(
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.getContent().stream().map(unitMapper::toResponse).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UnitResponse get(UUID id) {
        return unitMapper.toResponse(findById(id));
    }

    @Override
    @Transactional
    public UnitResponse create(CreateUnitRequest request) {
        String code = request.code().trim().toUpperCase();
        if (unitRepository.existsByCode(code)) {
            throw new BaseException(ErrorCode.INV_400_UNIT_CODE_EXISTED);
        }
        Unit unit = unitMapper.toEntity(new CreateUnitRequest(
                code, request.name().trim(), request.unitType().trim().toUpperCase()));
        return unitMapper.toResponse(unitRepository.save(unit));
    }

    @Override
    @Transactional
    public UnitResponse update(UUID id, UpdateUnitRequest request) {
        Unit unit = findById(id);
        String code = request.code().trim().toUpperCase();
        if (!unit.getCode().equals(code) && unitRepository.existsByCode(code)) {
            throw new BaseException(ErrorCode.INV_400_UNIT_CODE_EXISTED);
        }
        String unitType = request.unitType().trim().toUpperCase();
        if (!unit.getUnitType().equals(unitType) && materialRepository.existsByBaseUnitId(id)) {
            throw new BaseException(ErrorCode.INV_400_UNIT_IN_USE);
        }
        unitMapper.updateEntity(unit, new UpdateUnitRequest(code, request.name().trim(), unitType));
        return unitMapper.toResponse(unitRepository.save(unit));
    }

    @Override
    @Transactional
    public UnitResponse updateStatus(UUID id, String status) {
        Unit unit = findById(id);
        if (!StringUtils.hasText(status)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        String normalizedStatus = status.trim().toUpperCase();
        if (!"ACTIVE".equals(normalizedStatus) && !"INACTIVE".equals(normalizedStatus)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        unit.setStatus(normalizedStatus);
        return unitMapper.toResponse(unitRepository.save(unit));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Unit unit = findById(id);
        if (materialRepository.existsByBaseUnitId(unit.getId())) {
            throw new BaseException(ErrorCode.INV_400_UNIT_IN_USE);
        }
        unitRepository.deleteById(id);
    }

    private Unit findById(UUID id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_UNIT_NOT_FOUND));
    }
}
