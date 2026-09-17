package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ProductToppingMapper;
import com.erp.backend_service.repository.BranchToppingAvailabilityRepository;
import com.erp.backend_service.repository.ProductRepository;
import com.erp.backend_service.repository.ProductToppingRepository;
import com.erp.backend_service.repository.ToppingRepository;
import com.erp.backend_service.service.SalesToppingService;
import com.erp.core.domain.BranchToppingAvailability;
import com.erp.core.domain.ProductTopping;
import com.erp.core.domain.Topping;
import com.erp.core.dto.response.menu.ProductToppingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Topping cho kênh bán hàng: chỉ đọc, không ghi.
 * Không có branchId -> trả hết topping ACTIVE của SP (BE chặn lại lúc add cart nếu hết).
 * Có branchId -> lọc khả dụng tại chi nhánh để FE khỏi hiện món không gọi được.
 */
@Service
public class SalesToppingServiceImpl implements SalesToppingService {

    private final ProductRepository productRepository;
    private final ProductToppingRepository productToppingRepository;
    private final ToppingRepository toppingRepository;
    private final BranchToppingAvailabilityRepository branchToppingAvailabilityRepository;
    private final ProductToppingMapper mapper;

    public SalesToppingServiceImpl(ProductRepository productRepository,
                                   ProductToppingRepository productToppingRepository,
                                   ToppingRepository toppingRepository,
                                   BranchToppingAvailabilityRepository branchToppingAvailabilityRepository,
                                   ProductToppingMapper mapper) {
        this.productRepository = productRepository;
        this.productToppingRepository = productToppingRepository;
        this.toppingRepository = toppingRepository;
        this.branchToppingAvailabilityRepository = branchToppingAvailabilityRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductToppingResponse> listForSales(UUID productId, UUID branchId) {
        productRepository.findById(productId)
            .filter(p -> "ACTIVE".equals(p.getStatus()))
            .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND));

        List<ProductTopping> mappings = productToppingRepository.findByProductIdOrderByCreatedAtAsc(productId)
            .stream()
            .filter(pt -> "ACTIVE".equals(pt.getStatus()))
            .toList();
        if (mappings.isEmpty()) {
            return List.of();
        }
        Map<UUID, Topping> toppingMap = toppingRepository
            .findAllById(mappings.stream().map(ProductTopping::getToppingId).collect(Collectors.toSet()))
            .stream()
            .filter(t -> "ACTIVE".equals(t.getStatus()))
            .collect(Collectors.toMap(Topping::getId, Function.identity(), (a, b) -> a));

        final Map<UUID, BranchToppingAvailability> availabilityMap;
        if (branchId != null) {
            availabilityMap = branchToppingAvailabilityRepository
                .findByBranchIdAndToppingIds(branchId, toppingMap.keySet())
                .stream()
                .collect(Collectors.toMap(BranchToppingAvailability::getToppingId, Function.identity(),
                    (a, b) -> a));
        } else {
            availabilityMap = Map.of();
        }

        return mappings.stream()
            .filter(pt -> toppingMap.containsKey(pt.getToppingId()))
            .filter(pt -> branchId == null || isAvailableAtBranch(availabilityMap, pt.getToppingId()))
            .map(pt -> mapper.toResponse(pt, toppingMap.get(pt.getToppingId())))
            .toList();
    }

    private boolean isAvailableAtBranch(Map<UUID, BranchToppingAvailability> availabilityMap, UUID toppingId) {
        BranchToppingAvailability availability = availabilityMap.get(toppingId);
        return availability != null && "ACTIVE".equals(availability.getStatus()) && availability.isAvailable();
    }
}
