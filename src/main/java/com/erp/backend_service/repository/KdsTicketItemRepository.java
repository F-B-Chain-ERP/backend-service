package com.erp.backend_service.repository;

import com.erp.core.domain.KdsTicketItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KdsTicketItemRepository extends JpaRepository<KdsTicketItem, UUID> {

    List<KdsTicketItem> findByKdsTicketIdOrderByCreatedAtAsc(UUID kdsTicketId);

    List<KdsTicketItem> findByKdsTicketIdIn(List<UUID> kdsTicketIds);
}
