package com.erp.backend_service.repository;

import com.erp.core.domain.KdsTicketItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface KdsTicketItemRepository extends JpaRepository<KdsTicketItem, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from KdsTicketItem i where i.id = :id")
    java.util.Optional<KdsTicketItem> findByIdForUpdate(@Param("id") UUID id);

    List<KdsTicketItem> findByKdsTicketIdOrderByCreatedAtAsc(UUID kdsTicketId);

    List<KdsTicketItem> findByKdsTicketIdIn(List<UUID> kdsTicketIds);
}
