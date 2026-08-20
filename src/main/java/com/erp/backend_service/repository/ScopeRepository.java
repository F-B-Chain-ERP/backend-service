package com.erp.backend_service.repository;

import com.erp.core.domain.Scope;
import com.erp.core.enums.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Truy vấn dữ liệu phạm vi (Scope). */
@Repository
public interface ScopeRepository extends JpaRepository<Scope, UUID> {

    /** Tìm phạm vi theo loại phạm vi và chi nhánh. */
    Optional<Scope> findByScopeTypeAndBranchId(ScopeType scopeType, UUID branchId);
}
