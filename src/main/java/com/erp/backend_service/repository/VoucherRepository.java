package com.erp.backend_service.repository;

import com.erp.core.domain.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VoucherRepository extends JpaRepository<Voucher, UUID> {
    Optional<Voucher> findByCodeIgnoreCaseAndStatus(String code, String status);
}
