package com.erp.backend_service.repository;

import com.erp.core.domain.AccountRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccountRoleRepository extends JpaRepository<AccountRole, AccountRole.AccountRoleId> {
    List<AccountRole> findByAccountIdAndStatus(UUID accountId, String status);
    List<AccountRole> findByAccountId(UUID accountId);
}
