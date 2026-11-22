package com.DsaDude.Execution_Submission_Service.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class SubmissionAccess {
    private final String gatewayKey;
    private final String workerKey;

    public SubmissionAccess(@Value("${DSACHAMP_GATEWAY_KEY:}") String gatewayKey,
                            @Value("${DSACHAMP_WORKER_KEY:}") String workerKey) {
        this.gatewayKey = gatewayKey;
        this.workerKey = workerKey;
    }

    public int requireUser(String key, Integer userId) {
        if (!matches(gatewayKey, key) || userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }

    public int requireOwner(String key, Integer userId, int ownerId) {
        if (requireUser(key, userId) != ownerId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return ownerId;
    }

    public void requireWorker(String key) {
        if (!matches(workerKey, key)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private boolean matches(String expected, String supplied) {
        return !expected.isBlank() && supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
}
