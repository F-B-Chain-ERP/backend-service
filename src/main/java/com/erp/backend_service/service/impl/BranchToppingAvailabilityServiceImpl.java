package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.BranchToppingAvailabilityMapper;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.BranchToppingAvailabilityRepository;
import com.erp.backend_service.repository.ToppingRepository;
import com.erp.backend_service.service.BranchToppingAvailabilityService;
import com.erp.core.domain.BranchToppingAvailability;
import com.erp.core.domain.Topping;
import com.erp.core.dto.request.menu.UpdateBranchToppingRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.BranchToppingAvailabilityResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BranchToppingAvailabilityServiceImpl implements BranchToppingAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(BranchToppingAvailabilityServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final BranchToppingAvailabilityRepository repository;
    private final BranchRepository branchRepository;
    private final ToppingRepository toppingRepository;
    private final BranchToppingAvailabilityMapper mapper;

    public BranchToppingAvailabilityServiceImpl(
            BranchToppingAvailabilityRepository repository,
            BranchRepository branchRepository,
            ToppingRepository toppingRepository,
            BranchToppingAvailabilityMapper mapper
    ) {
        this.repository = repository;
        this.branchRepository = branchRepository;
        this.toppingRepository = toppingRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BranchToppingAvailabilityResponse> list(
            UUID branchId, int page, int size, String search, String status) {
        log.info("Lấy danh sách topping availability chi nhánh: {}, search={}, status={}", branchId, search, status);

        branchRepository.findById(branchId)
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND));

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        Page<BranchToppingAvailability> result = repository.search(branchId, search, status, pageable);

        Map<UUID, Topping> toppingMap = loadToppings(result.getContent());
        List<BranchToppingAvailabilityResponse> items = result.getContent().stream()
                .map(bta -> mapper.toResponse(bta, toppingMap.get(bta.getToppingId())))
                .toList();

        return new PageResponse<>(result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), items);
    }

    @Override
    @Transactional
    public BranchToppingAvailabilityResponse updateAvailability(
            UUID branchId, UUID toppingId, UpdateBranchToppingRequest request) {
        log.info("Upsert topping availability: branchId={}, toppingId={}, isAvailable={}",
                branchId, toppingId, request.isAvailable());

        branchRepository.findById(branchId)
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND));

        Topping topping = toppingRepository.findById(toppingId)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_TOPPING_NOT_FOUND));

        Optional<BranchToppingAvailability> existing =
                repository.findByBranchIdAndToppingId(branchId, toppingId);

        BranchToppingAvailability bta;
        if (existing.isPresent()) {
            bta = existing.get();
            bta.setAvailable(Boolean.TRUE.equals(request.isAvailable()));
        } else {
            bta = new BranchToppingAvailability();
            bta.setBranchId(branchId);
            bta.setToppingId(toppingId);
            bta.setAvailable(Boolean.TRUE.equals(request.isAvailable()));
            bta.setStatus("ACTIVE");
        }

        return mapper.toResponse(repository.save(bta), topping);
    }

    private Map<UUID, Topping> loadToppings(List<BranchToppingAvailability> btas) {
        Set<UUID> ids = btas.stream().map(BranchToppingAvailability::getToppingId).collect(Collectors.toSet());
        return toppingRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Topping::getId, t -> t, (a, b) -> a));
    }
}
