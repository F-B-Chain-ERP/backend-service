package com.erp.backend_service.security.oauth2;

import com.erp.backend_service.exception.BadRequestException;
import com.erp.backend_service.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Xác minh Google ID token (JWT credential) trả về từ Google Sign-In phía client.
 * Sử dụng endpoint {@code tokeninfo} của Google để kiểm tra chữ ký, audience,
 * issuer và thời hạn. Không lưu trữ bất kỳ bí mật nào.
 */
@Component
public class GoogleTokenVerifier {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final RestTemplate restTemplate = new RestTemplate();
    private final String clientId;

    public GoogleTokenVerifier(@Value("${app.oauth2.google.client-id}") String clientId) {
        this.clientId = clientId;
    }

    /**
     * Xác minh id token và trích xuất thông tin người dùng Google.
     *
     * @param idToken Google ID token (JWT credential)
     * @return thông tin người dùng đã xác minh
     */
    public GoogleUserInfo verify(String idToken) {
        Map<String, Object> claims;
        try {
            claims = restTemplate.getForObject(TOKENINFO_URL + "?id_token=" + idToken, Map.class);
        } catch (RestClientException exception) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }
        if (claims == null || claims.isEmpty()) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }
        validate(claims);
        return new GoogleUserInfo(
                (String) claims.get("sub"),
                (String) claims.get("email"),
                Boolean.parseBoolean(String.valueOf(claims.get("email_verified"))),
                (String) claims.get("name"),
                (String) claims.get("picture")
        );
    }

    /** Kiểm tra aud, iss và thời hạn của token. */
    private void validate(Map<String, Object> claims) {
        String aud = (String) claims.get("aud");
        if (aud == null || !aud.equals(clientId)) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }
        String iss = (String) claims.get("iss");
        if (iss == null || !ISSUERS.contains(iss)) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }
        Object expObj = claims.get("exp");
        if (expObj == null) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }
        long exp = Long.parseLong(String.valueOf(expObj));
        if (Instant.now().getEpochSecond() >= exp) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }
    }
}
