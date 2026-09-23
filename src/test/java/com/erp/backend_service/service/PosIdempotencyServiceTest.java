package com.erp.backend_service.service;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.repository.IdempotencyKeyRepository;
import com.erp.backend_service.service.pos.PosIdempotencyService;
import com.erp.core.domain.IdempotencyKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosIdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository repository;

    @Test
    void replayChoosesSucceededRecordWhenServerAlreadyContainsDuplicates() {
        PosIdempotencyService service = new PosIdempotencyService(repository);
        UUID orderId = UUID.randomUUID();
        IdempotencyKey failed = record("hash", "FAILED", null);
        IdempotencyKey succeeded = record("hash", "SUCCEEDED", orderId);
        when(repository.findAllByIdempotencyKeyOrderByCreatedAtDesc("key"))
            .thenReturn(List.of(failed, succeeded));

        Optional<UUID> replayed = service.replayOrderId("key", "hash");

        assertEquals(Optional.of(orderId), replayed);
    }

    @Test
    void claimLocksKeyAndCleansAllDeadDuplicatesBeforeCreatingRecord() {
        PosIdempotencyService service = new PosIdempotencyService(repository);
        IdempotencyKey first = record("hash", "FAILED", null);
        IdempotencyKey second = record("hash", "PROCESSING", null);
        second.setExpiresAt(Instant.now().minusSeconds(1));
        when(repository.findAllByIdempotencyKeyOrderByCreatedAtDesc("key"))
            .thenReturn(List.of(first, second));
        when(repository.save(any(IdempotencyKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IdempotencyKey claimed = service.claim("key", "hash");

        verify(repository).acquireTransactionLock(anyLong());
        verify(repository).deleteAll(List.of(first, second));
        assertEquals("PROCESSING", claimed.getStatus());
        assertEquals("key", claimed.getIdempotencyKey());
    }

    @Test
    void claimRejectsLiveRecordAfterTakingCrossInstanceLock() {
        PosIdempotencyService service = new PosIdempotencyService(repository);
        IdempotencyKey processing = record("hash", "PROCESSING", null);
        processing.setExpiresAt(Instant.now().plusSeconds(60));
        when(repository.findAllByIdempotencyKeyOrderByCreatedAtDesc("key"))
            .thenReturn(List.of(processing));

        assertThrows(BaseException.class, () -> service.claim("key", "hash"));
        verify(repository).acquireTransactionLock(anyLong());
    }

    private IdempotencyKey record(String hash, String status, UUID orderId) {
        IdempotencyKey record = new IdempotencyKey();
        record.setId(UUID.randomUUID());
        record.setIdempotencyKey("key");
        record.setRequestHash(hash);
        record.setStatus(status);
        record.setExpiresAt(Instant.now().plusSeconds(60));
        if (orderId != null) {
            record.setResponsePayload(Map.of("orderId", orderId.toString()));
        }
        return record;
    }
}
