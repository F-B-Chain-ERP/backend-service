package com.erp.backend_service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordTest {

    @Test
    void testPasswordHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode("123456789");
        System.out.println("GEN_HASH_123456789=" + hash);
        assertTrue(encoder.matches("123456789", hash));
        assertTrue(encoder.matches("123456789", "$2a$12$RFmydSknLc2h.UowCy34yeB1vvP1Y0vTFeA6gH/se8bbwS26rGThm"));
    }
}
