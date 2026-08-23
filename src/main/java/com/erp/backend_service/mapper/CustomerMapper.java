package com.erp.backend_service.mapper;

import com.erp.core.domain.Customer;
import com.erp.core.dto.response.CustomerDetailResponse;
import org.springframework.stereotype.Component;

/** Chuyển đổi entity Customer sang DTO phản hồi CustomerDetailResponse. */
@Component
public class CustomerMapper {

    /** Ánh xạ thông tin khách hàng sang response (ẩn mật khẩu). */
    public CustomerDetailResponse toResponse(Customer customer) {
        return new CustomerDetailResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getUsername(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getAuthProvider(),
                customer.isHasLocalPassword(),
                customer.isEmailVerified(),
                customer.getAvatarUrl(),
                customer.getDateOfBirth(),
                customer.getGender(),
                customer.getStatus(),
                customer.getLastLoginAt(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
