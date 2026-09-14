package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ToppingMapper;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.ProductToppingRepository;
import com.erp.backend_service.repository.ToppingRepository;
import com.erp.backend_service.service.ToppingService;
import com.erp.core.domain.Material;
import com.erp.core.domain.Topping;
import com.erp.core.dto.request.menu.CreateToppingRequest;
import com.erp.core.dto.request.menu.UpdateToppingRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.ToppingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Triển khai {@link ToppingService}: quản lý topping master với kiểm tra trùng mã,
 * validate mapping material (materialId ↔ materialQuantity) và hard delete an toàn.
 */
@Service
public class ToppingServiceImpl implements ToppingService {

    private static final Logger log = LoggerFactory.getLogger(ToppingServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final ToppingRepository toppingRepository;
    private final ProductToppingRepository productToppingRepository;
    private final MaterialRepository materialRepository;
    private final ToppingMapper toppingMapper;

    public ToppingServiceImpl(
            ToppingRepository toppingRepository,
            ProductToppingRepository productToppingRepository,
            MaterialRepository materialRepository,
            ToppingMapper toppingMapper
    ) {
        this.toppingRepository = toppingRepository;
        this.productToppingRepository = productToppingRepository;
        this.materialRepository = materialRepository;
        this.toppingMapper = toppingMapper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ToppingResponse> list(int page, int size, String search, String groupName, String status) {
        log.info("Lấy danh sách topping, search={}, groupName={}, status={}", search, groupName, status);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Topping> result = toppingRepository.search(search, groupName, status, pageable);
        List<ToppingResponse> items = result.getContent().stream()
                .map(toppingMapper::toResponse)
                .toList();
        return new PageResponse<>(result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), items);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ToppingResponse get(UUID id) {
        log.info("Lấy chi tiết topping: {}", id);
        Topping t = toppingRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_TOPPING_NOT_FOUND));
        return toppingMapper.toResponse(t);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ToppingResponse create(CreateToppingRequest request) {
        log.info("Tạo topping mới: code={}", request.code());
        if (toppingRepository.existsByCode(request.code())) {
            throw new BaseException(ErrorCode.MENU_409_TOPPING_CODE_EXISTED);
        }
        validateMaterialMapping(request.materialId(), request.materialQuantity());

        Topping t = new Topping();
        applyFields(t, request);
        t.setStatus("ACTIVE");
        return toppingMapper.toResponse(toppingRepository.save(t));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ToppingResponse update(UUID id, UpdateToppingRequest request) {
        log.info("Cập nhật topping: {}", id);
        Topping t = toppingRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_TOPPING_NOT_FOUND));

        if (toppingRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new BaseException(ErrorCode.MENU_409_TOPPING_CODE_EXISTED);
        }
        validateMaterialMapping(request.materialId(), request.materialQuantity());

        applyFieldsUpdate(t, request);

        String status = request.status();
        if (status != null && !status.isBlank()) {
            String normalized = status.toUpperCase();
            if (!List.of("ACTIVE", "INACTIVE").contains(normalized)) {
                throw new BaseException(ErrorCode.MENU_400_INVALID_TOPPING_DATA,
                        "Trạng thái chỉ chấp nhận ACTIVE hoặc INACTIVE.");
            }
            t.setStatus(normalized);
        }

        return toppingMapper.toResponse(toppingRepository.save(t));
    }

    /** {@inheritDoc} — hard delete, kiểm tra topping chưa được gán cho sản phẩm. */
    @Override
    @Transactional
    public void delete(UUID id) {
        log.info("Xóa topping (hard delete): {}", id);
        toppingRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_TOPPING_NOT_FOUND));

        if (productToppingRepository.existsByToppingId(id)) {
            throw new BaseException(ErrorCode.MENU_409_TOPPING_IN_USE);
        }
        toppingRepository.deleteById(id);
    }

    private void validateMaterialMapping(UUID materialId, BigDecimal materialQuantity) {
        boolean hasId = materialId != null;
        boolean hasQty = materialQuantity != null && materialQuantity.compareTo(BigDecimal.ZERO) > 0;
        if (hasId != hasQty) {
            throw new BaseException(ErrorCode.MENU_400_INVALID_TOPPING_DATA);
        }
        if (hasId) {
            Material m = materialRepository.findById(materialId)
                    .orElseThrow(() -> new BaseException(ErrorCode.INV_404_MATERIAL_NOT_FOUND));
            if (!"ACTIVE".equalsIgnoreCase(m.getStatus())) {
                throw new BaseException(ErrorCode.MENU_400_INVALID_TOPPING_DATA,
                        "Nguyên vật liệu liên kết phải ở trạng thái ACTIVE.");
            }
        }
    }

    private void applyFields(Topping t, CreateToppingRequest request) {
        t.setCode(request.code());
        t.setName(request.name());
        t.setPrice(request.price());
        t.setImageUrl(request.imageUrl());
        t.setGroupName(request.groupName());
        t.setMaterialId(request.materialId());
        t.setMaterialQuantity(request.materialQuantity());
    }

    private void applyFieldsUpdate(Topping t, UpdateToppingRequest request) {
        t.setCode(request.code());
        t.setName(request.name());
        t.setPrice(request.price());
        t.setImageUrl(request.imageUrl());
        t.setGroupName(request.groupName());
        t.setMaterialId(request.materialId());
        t.setMaterialQuantity(request.materialQuantity());
    }
}
