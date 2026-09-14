package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.BranchProductAvailabilityMapper;
import com.erp.backend_service.repository.BranchProductAvailabilityRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.CategoryRepository;
import com.erp.backend_service.repository.ProductRepository;
import com.erp.backend_service.service.BranchProductAvailabilityService;
import com.erp.core.domain.BranchProductAvailability;
import com.erp.core.domain.Category;
import com.erp.core.domain.Product;
import com.erp.core.dto.request.menu.UpdateBranchProductRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.BranchProductAvailabilityResponse;
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
public class BranchProductAvailabilityServiceImpl implements BranchProductAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(BranchProductAvailabilityServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final BranchProductAvailabilityRepository repository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BranchProductAvailabilityMapper mapper;

    public BranchProductAvailabilityServiceImpl(
            BranchProductAvailabilityRepository repository,
            BranchRepository branchRepository,
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            BranchProductAvailabilityMapper mapper
    ) {
        this.repository = repository;
        this.branchRepository = branchRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BranchProductAvailabilityResponse> list(
            UUID branchId, int page, int size, String search, String status, UUID categoryId) {
        log.info("Lấy danh sách product availability chi nhánh: {}, search={}, status={}, categoryId={}", branchId, search, status, categoryId);

        branchRepository.findById(branchId)
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND));

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);

        Page<Product> products = productRepository.findActiveForAvailability(search, categoryId, pageable);

        Set<UUID> productIds = products.getContent().stream()
                .map(Product::getId)
                .collect(Collectors.toSet());

        Map<UUID, BranchProductAvailability> availabilityMap = repository
                .findByBranchIdAndProductIds(branchId, productIds)
                .stream()
                .collect(Collectors.toMap(BranchProductAvailability::getProductId, bpa -> bpa, (a, b) -> a));

        Map<UUID, String> categoryNameMap = resolveCategoryNames(products.getContent());

        List<BranchProductAvailabilityResponse> items = products.getContent().stream()
                .map(product -> {
                    BranchProductAvailability bpa = availabilityMap.get(product.getId());
                    String categoryName = categoryNameMap.get(product.getCategoryId());
                    if (bpa != null) {
                        return mapper.toResponse(bpa, product, categoryName);
                    }
                    return mapper.toResponseAvailableByDefault(product, categoryName);
                })
                .toList();

        return new PageResponse<>(products.getNumber(), products.getSize(),
                products.getTotalElements(), products.getTotalPages(), items);
    }

    @Override
    @Transactional
    public BranchProductAvailabilityResponse updateAvailability(
            UUID branchId, UUID productId, UpdateBranchProductRequest request) {
        log.info("Update product availability: branchId={}, productId={}, isAvailable={}, salePrice={}, clearPrice={}",
                branchId, productId, request.isAvailable(), request.salePrice(), request.clearPrice());

        branchRepository.findById(branchId)
                .orElseThrow(() -> new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND));

        String categoryName = product.getCategoryId() != null
                ? categoryRepository.findById(product.getCategoryId()).map(Category::getName).orElse(null)
                : null;

        Optional<BranchProductAvailability> existing =
                repository.findByBranchIdAndProductId(branchId, productId);

        if (Boolean.TRUE.equals(request.clearPrice())) {
            return clearPrice(branchId, productId, request.isAvailable(), existing, product, categoryName);
        }

        boolean targetAvailable = Boolean.TRUE.equals(request.isAvailable());
        boolean priceProvided = request.salePrice() != null;

        if (priceProvided) {
            BranchProductAvailability bpa = existing.orElse(new BranchProductAvailability());
            bpa.setBranchId(branchId);
            bpa.setProductId(productId);
            bpa.setAvailable(targetAvailable);
            bpa.setSalePrice(request.salePrice());
            if (bpa.getStatus() == null) bpa.setStatus("ACTIVE");
            return mapper.toResponse(repository.save(bpa), product, categoryName);
        }

        if (existing.isEmpty()) {
            if (targetAvailable) {
                return mapper.toResponseAvailableByDefault(product, categoryName);
            }
            BranchProductAvailability bpa = new BranchProductAvailability();
            bpa.setBranchId(branchId);
            bpa.setProductId(productId);
            bpa.setAvailable(false);
            bpa.setSalePrice(null);
            bpa.setStatus("ACTIVE");
            return mapper.toResponse(repository.save(bpa), product, categoryName);
        }

        BranchProductAvailability bpa = existing.get();
        bpa.setAvailable(targetAvailable);
        if (targetAvailable && bpa.getSalePrice() == null) {
            repository.delete(bpa);
            return mapper.toResponseAvailableByDefault(product, categoryName);
        }
        return mapper.toResponse(repository.save(bpa), product, categoryName);
    }

    private BranchProductAvailabilityResponse clearPrice(
            UUID branchId, UUID productId, Boolean isAvailable,
            Optional<BranchProductAvailability> existing,
            Product product, String categoryName) {
        boolean targetAvailable = Boolean.TRUE.equals(isAvailable);

        if (existing.isEmpty()) {
            return mapper.toResponseAvailableByDefault(product, categoryName);
        }
        BranchProductAvailability bpa = existing.get();

        if (bpa.isAvailable() || targetAvailable) {
            repository.delete(bpa);
            return mapper.toResponseAvailableByDefault(product, categoryName);
        }

        bpa.setSalePrice(null);
        bpa.setAvailable(false);
        return mapper.toResponse(repository.save(bpa), product, categoryName);
    }

    private Map<UUID, String> resolveCategoryNames(List<Product> products) {
        Set<UUID> categoryIds = products.stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (categoryIds.isEmpty()) return Map.of();
        return categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    }
}
