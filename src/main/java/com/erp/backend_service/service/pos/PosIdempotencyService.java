package com.erp.backend_service.service.pos;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.IdempotencyKeyRepository;
import com.erp.core.domain.IdempotencyKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Chống tạo trùng đơn khi F5/double-click/retry mạng.
 * Server là chuẩn và không có unique constraint nên đây là rào application-level:
 * chặn 99% trùng tuần tự; race tuyệt đối cần thêm unique index
 * {@code uq_idempotency_key} chạy trực tiếp trên server (xem SQL cuối file).
 *
 * <pre>
 * CREATE UNIQUE INDEX IF NOT EXISTS uq_idempotency_key ON idempotency_key (idempotency_key);
 * </pre>
 */
@Service
public class PosIdempotencyService {

    private final IdempotencyKeyRepository repository;

    public PosIdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    public String hash(UUID customerId, String canonicalRequest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                (customerId + "|" + canonicalRequest).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new BaseException(ErrorCode.INTERNAL_ERROR, "Không tính được request hash.");
        }
    }

    public Optional<IdempotencyKey> find(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return repository.findAllByIdempotencyKeyOrderByCreatedAtDesc(key.trim()).stream().findFirst();
    }

    /**
     * Replay: key đã SUCCEEDED + cùng hash (đã gồm customerId) -> trả orderId cũ để caller load lại.
     * Cùng key khác hash -> 409 (key bị tái dùng sai). PROCESSING chưa hết hạn -> 409 retry sau.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> replayOrderId(String key, String hash) {
        List<IdempotencyKey> records = repository.findAllByIdempotencyKeyOrderByCreatedAtDesc(key.trim());
        if (records.isEmpty()) {
            return Optional.empty();
        }
        List<IdempotencyKey> matching = records.stream()
            .filter(record -> hash.equals(record.getRequestHash())).toList();
        if (matching.isEmpty()) {
            throw new BaseException(ErrorCode.DUPLICATE_RESOURCE,
                "Idempotency-Key đã dùng cho đơn khác, vui lòng tạo key mới.");
        }
        IdempotencyKey record = matching.stream()
            .filter(r -> "SUCCEEDED".equals(r.getStatus()))
            .findFirst().orElse(matching.get(0));
        if ("SUCCEEDED".equals(record.getStatus())) {
            Object orderId = record.getResponsePayload() == null ? null : record.getResponsePayload().get("orderId");
            if (orderId != null) {
                return Optional.of(UUID.fromString(orderId.toString()));
            }
            return Optional.empty();
        }
        boolean processing = matching.stream().anyMatch(r -> "PROCESSING".equals(r.getStatus())
            && r.getExpiresAt() != null && r.getExpiresAt().isAfter(Instant.now()));
        if (processing) {
            throw new BaseException(ErrorCode.DUPLICATE_RESOURCE,
                "Đơn đang được xử lý, vui lòng thử lại sau giây lát.");
        }
        return Optional.empty();
    }

    @Transactional
    public IdempotencyKey claim(String key, String hash) {
        String normalizedKey = key.trim();
        repository.acquireTransactionLock(lockId(normalizedKey));
        List<IdempotencyKey> existingRecords =
            repository.findAllByIdempotencyKeyOrderByCreatedAtDesc(normalizedKey);
        if (!existingRecords.isEmpty()) {
            boolean hasLive = existingRecords.stream().anyMatch(rec ->
                !("FAILED".equals(rec.getStatus()) ||
                    (rec.getExpiresAt() != null && rec.getExpiresAt().isBefore(Instant.now()))));
            if (hasLive) {
                throw new BaseException(ErrorCode.DUPLICATE_RESOURCE,
                    "Key đang được xử lý hoặc đã dùng, vui lòng dùng key mới.");
            }
            repository.deleteAll(existingRecords);
        }
        IdempotencyKey record = new IdempotencyKey();
        record.setIdempotencyKey(normalizedKey);
        record.setRequestHash(hash);
        record.setStatus("PROCESSING");
        record.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        return repository.save(record);
    }

    private long lockId(String key) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(("IDEMPOTENCY|" + key).getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(bytes).getLong();
        } catch (Exception e) {
            throw new BaseException(ErrorCode.INTERNAL_ERROR, "Không tạo được idempotency lock.");
        }
    }

    @Transactional
    public void succeeded(IdempotencyKey record, UUID orderId) {
        record.setStatus("SUCCEEDED");
        record.setResponsePayload(Map.of("orderId", orderId.toString()));
        repository.save(record);
    }

}
