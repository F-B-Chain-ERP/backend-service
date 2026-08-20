package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.RoleRepository;
import com.erp.backend_service.service.RoleService;
import com.erp.core.domain.Role;
import com.erp.core.dto.auth.RoleResponse;
import com.erp.core.dto.request.role.CreateRoleRequest;
import com.erp.core.dto.request.role.UpdateRoleRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.enums.RoleType;
import com.erp.core.enums.EntityStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    public RoleServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public RoleResponse create(CreateRoleRequest request) {
        String code = request.name().toUpperCase();
        if (roleRepository.findByCode(code).isPresent()) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Role role = new Role();
        role.setCode(code);
        role.setName(request.name());
        role.setDescription(request.description());
        role.setType(request.type());
        role.setStatus(request.status());

        Role saved = roleRepository.save(role);
        return toResponse(saved);
    }

    @Override
    public RoleResponse getById(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        return toResponse(role);
    }

    @Override
    public RoleResponse getByCode(String code) {
        Role role = roleRepository.findByCode(code)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        return toResponse(role);
    }

    @Override
    public PageResponse<RoleResponse> getAll(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Role> rolePage;

        if (search != null && !search.trim().isEmpty()) {
            rolePage = roleRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(search, search, pageable);
        } else {
            rolePage = roleRepository.findAll(pageable);
        }

        return new PageResponse<>(
                rolePage.getNumber(),
                rolePage.getSize(),
                rolePage.getTotalElements(),
                rolePage.getTotalPages(),
                rolePage.getContent().stream().map(this::toResponse).toList()
        );
    }

    @Override
    public RoleResponse update(UUID id, UpdateRoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));

        String code = request.name().toUpperCase();
        Optional<Role> existingByCode = roleRepository.findByCode(code);
        if (existingByCode.isPresent() && !existingByCode.get().getId().equals(id)) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        role.setName(request.name());
        role.setCode(code);
        role.setDescription(request.description());
        role.setType(request.type());
        role.setStatus(request.status());

        Role updated = roleRepository.save(role);
        return toResponse(updated);
    }

    @Override
    public void delete(UUID id) {
        if (!roleRepository.existsById(id)) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        roleRepository.deleteById(id);
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(
                role.getId().toString(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getType(),
                role.getStatus()
        );
    }
}