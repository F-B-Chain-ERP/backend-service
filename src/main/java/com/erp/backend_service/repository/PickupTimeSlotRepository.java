package com.erp.backend_service.repository;

import com.erp.core.domain.PickupTimeSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PickupTimeSlotRepository extends JpaRepository<PickupTimeSlot, UUID> {

    List<PickupTimeSlot> findByBranchIdOrderByStartTimeAsc(UUID branchId);

    List<PickupTimeSlot> findByBranchIdAndStatusOrderByStartTimeAsc(UUID branchId, String status);

    Optional<PickupTimeSlot> findByIdAndBranchId(UUID id, UUID branchId);

    Optional<PickupTimeSlot> findByBranchIdAndSlotCode(UUID branchId, String slotCode);

    boolean existsByBranchIdAndSlotCode(UUID branchId, String slotCode);

    boolean existsByBranchIdAndSlotCodeAndIdNot(UUID branchId, String slotCode, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PickupTimeSlot s where s.id = :id")
    Optional<PickupTimeSlot> findByIdForUpdate(@Param("id") UUID id);

    void deleteByBranchId(UUID branchId);
}
