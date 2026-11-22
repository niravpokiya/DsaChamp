package com.DsaDude.Execution_Submission_Service.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ContestServiceAccess {
    private final byte[] contestKey;

    public ContestServiceAccess(@Value("${DSACHAMP_CONTEST_KEY:}") String contestKey) {
        this.contestKey = contestKey.getBytes(StandardCharsets.UTF_8);
    }

    public void requireContestService(String key) {
        if (contestKey.length == 0 || key == null
                || !MessageDigest.isEqual(contestKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
