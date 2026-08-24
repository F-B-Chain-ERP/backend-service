package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.SupplierMaterialMapper;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.SupplierMaterialRepository;
import com.erp.backend_service.repository.SupplierRepository;
import com.erp.backend_service.service.SupplierMaterialService;
import com.erp.core.domain.Material;
import com.erp.core.domain.Supplier;
import com.erp.core.domain.SupplierMaterial;
import com.erp.core.dto.request.proc.CreateSupplierMaterialRequest;
import com.erp.core.dto.request.proc.UpdateSupplierMaterialRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.SupplierMaterialResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SupplierMaterialServiceImpl implements SupplierMaterialService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String DEFAULT_STATUS = "ACTIVE";

    private final SupplierMaterialRepository supplierMaterialRepository;
    private final SupplierRepository supplierRepository;
    private final MaterialRepository materialRepository;
    private final SupplierMaterialMapper supplierMaterialMapper;

    public SupplierMaterialServiceImpl(SupplierMaterialRepository supplierMaterialRepository,
                                        SupplierRepository supplierRepository,
                                        MaterialRepository materialRepository,
                                        SupplierMaterialMapper supplierMaterialMapper) {
        this.supplierMaterialRepository = supplierMaterialRepository;
        this.supplierRepository = supplierRepository;
        this.materialRepository = materialRepository;
        this.supplierMaterialMapper = supplierMaterialMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SupplierMaterialResponse> list(int page, int size, UUID supplierId, UUID materialId, String search) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending());

        Page<SupplierMaterial> pageResult;
        if (supplierId != null && materialId != null) {
            pageResult = supplierMaterialRepository.findBySupplierIdAndMaterialId(supplierId, materialId, pageable);
        } else if (supplierId != null) {
            pageResult = supplierMaterialRepository.findBySupplierId(supplierId, pageable);
        } else if (materialId != null) {
            pageResult = supplierMaterialRepository.findByMaterialId(materialId, pageable);
        } else if (StringUtils.hasText(search)) {
            pageResult = supplierMaterialRepository.findBySupplierSkuContainingIgnoreCase(search.trim(), pageable);
        } else {
            pageResult = supplierMaterialRepository.findAll(pageable);
        }
        return toPageResponse(pageResult);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierMaterialResponse get(UUID id) {
        return toResponseWithNames(findById(id));
    }

    @Override
    @Transactional
    public SupplierMaterialResponse create(CreateSupplierMaterialRequest request) {
        if (!supplierRepository.existsById(request.supplierId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!materialRepository.existsById(request.materialId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (supplierMaterialRepository.existsBySupplierIdAndMaterialId(request.supplierId(), request.materialId())) {
            throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
        }
        SupplierMaterial entity = new SupplierMaterial();
        apply(entity, request.supplierId(), request.materialId(), request.supplierSku(),
                request.purchasePrice(), request.leadTimeDays(), request.isPreferred(), request.status());
        return toResponseWithNames(supplierMaterialRepository.save(entity));
    }

    @Override
    @Transactional
    public SupplierMaterialResponse update(UUID id, UpdateSupplierMaterialRequest request) {
        SupplierMaterial entity = findById(id);
        if (!supplierRepository.existsById(request.supplierId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!materialRepository.existsById(request.materialId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (supplierMaterialRepository.existsBySupplierIdAndMaterialIdAndIdNot(request.supplierId(), request.materialId(), id)) {
            throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
        }
        apply(entity, request.supplierId(), request.materialId(), request.supplierSku(),
                request.purchasePrice(), request.leadTimeDays(), request.isPreferred(), request.status());
        return toResponseWithNames(supplierMaterialRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!supplierMaterialRepository.existsById(id)) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        supplierMaterialRepository.deleteById(id);
    }

    private SupplierMaterial findById(UUID id) {
        return supplierMaterialRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void apply(SupplierMaterial e, UUID supplierId, UUID materialId, String supplierSku,
                       BigDecimal purchasePrice, Integer leadTimeDays, Boolean isPreferred, String status) {
        e.setSupplierId(supplierId);
        e.setMaterialId(materialId);
        e.setSupplierSku(supplierSku);
        e.setPurchasePrice(purchasePrice);
        e.setLeadTimeDays(leadTimeDays != null ? leadTimeDays : 1);
        e.setPreferred(isPreferred != null && isPreferred);
        e.setStatus(status != null && !status.isBlank() ? status : DEFAULT_STATUS);
    }

    private SupplierMaterialResponse toResponseWithNames(SupplierMaterial e) {
        Map<UUID, String> supplierNames = resolveNames(List.of(e.getSupplierId()),
                supplierRepository::findById, Supplier::getId, Supplier::getName);
        Map<UUID, String> materialNames = resolveNames(List.of(e.getMaterialId()),
                materialRepository::findById, Material::getId, Material::getName);
        return supplierMaterialMapper.toResponse(e, supplierNames.get(e.getSupplierId()), materialNames.get(e.getMaterialId()));
    }

    private PageResponse<SupplierMaterialResponse> toPageResponse(Page<SupplierMaterial> pageResult) {
        List<SupplierMaterial> items = pageResult.getContent();
        Map<UUID, String> supplierNames = resolveNames(
                items.stream().map(SupplierMaterial::getSupplierId).filter(Objects::nonNull).distinct().toList(),
                supplierRepository::findById, Supplier::getId, Supplier::getName);
        Map<UUID, String> materialNames = resolveNames(
                items.stream().map(SupplierMaterial::getMaterialId).filter(Objects::nonNull).distinct().toList(),
                materialRepository::findById, Material::getId, Material::getName);

        List<SupplierMaterialResponse> content = items.stream()
                .map(e -> supplierMaterialMapper.toResponse(
                        e, supplierNames.get(e.getSupplierId()), materialNames.get(e.getMaterialId())))
                .toList();

        return new PageResponse<>(pageResult.getNumber(), pageResult.getSize(),
                pageResult.getTotalElements(), pageResult.getTotalPages(), content);
    }


    private <T> Map<UUID, String> resolveNames(List<UUID> ids,
                                               Function<UUID, Optional<T>> finder,
                                               Function<T, UUID> idExtractor,
                                               Function<T, String> nameExtractor) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return ids.stream()
                .map(finder)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(idExtractor, nameExtractor, (a, b) -> a));
    }
}
