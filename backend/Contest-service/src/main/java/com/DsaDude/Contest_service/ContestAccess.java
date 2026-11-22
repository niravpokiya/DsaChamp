package com.DsaDude.Contest_service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ContestAccess {
    private final byte[] gatewayKey;

    public ContestAccess(@Value("${DSACHAMP_GATEWAY_KEY:}") String gatewayKey) {
        this.gatewayKey = gatewayKey.getBytes(StandardCharsets.UTF_8);
    }

    public int requireUser(String key, Integer userId) {
        if (gatewayKey.length == 0 || key == null || userId == null || userId <= 0
                || !MessageDigest.isEqual(gatewayKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }

    public void requireAdmin(String key, Integer userId, String role) {
        requireUser(key, userId);
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
