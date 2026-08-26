package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.MaterialMapper;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.service.MaterialService;
import com.erp.core.domain.Material;
import com.erp.core.dto.response.Material.MaterialResponse;
import com.erp.core.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class MaterialServiceImpl implements MaterialService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MaterialRepository materialRepository;
    private final MaterialMapper materialMapper;

    public MaterialServiceImpl(MaterialRepository materialRepository, MaterialMapper materialMapper) {
        this.materialRepository = materialRepository;
        this.materialMapper = materialMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MaterialResponse> list(int page, int size, String search) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending());
        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : "";
        Page<Material> pageResult = materialRepository.search(normalizedSearch, pageable);
        return new PageResponse<>(
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.getContent().stream().map(materialMapper::toResponse).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialResponse get(UUID id) {
        return materialRepository.findById(id)
                .map(materialMapper::toResponse)
                .orElseThrow(() -> new BaseException(ErrorCode.MATERIAL_NOT_FOUND));
    }
}
