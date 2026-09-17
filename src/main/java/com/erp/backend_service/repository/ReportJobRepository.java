package com.erp.backend_service.repository;

import com.erp.core.domain.ReportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository truy xuất tác vụ xuất báo cáo (ReportJob).
 */
@Repository
public interface ReportJobRepository extends JpaRepository<ReportJob, UUID>, JpaSpecificationExecutor<ReportJob> {

    Page<ReportJob> findByRequestedByOrderByCreatedAtDesc(UUID requestedBy, Pageable pageable);

    Optional<ReportJob> findByIdAndRequestedBy(UUID id, UUID requestedBy);

    List<ReportJob> findByStatusAndCreatedAtBefore(String status, Instant cutoff);
}
