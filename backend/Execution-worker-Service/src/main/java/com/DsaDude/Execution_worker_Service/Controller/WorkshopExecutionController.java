package com.DsaDude.Execution_worker_Service.Controller;

import com.DsaDude.Execution_worker_Service.DTO.ExecutionJob;
import com.DsaDude.Execution_worker_Service.DTO.ExecutionResult;
import com.DsaDude.Execution_worker_Service.Service.ContainerPoolManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

@RestController
@RequestMapping("/internal/workshop")
class WorkshopExecutionController {
    private static final int MAX_CONTENT_LENGTH = 65_536;
    private static final Set<String> LANGUAGES = Set.of("cpp", "java", "python");

    private final Semaphore availableRuns = new Semaphore(2);
    private final ContainerPoolManager runners;
    private final byte[] workshopKey;

    WorkshopExecutionController(ContainerPoolManager runners, @Value("${DSACHAMP_WORKSHOP_KEY:}") String key) {
        this.runners = runners;
        this.workshopKey = key.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/run")
    CompletableFuture<ExecutionResult> run(
            @RequestHeader(value = "X-DSACHAMP-WORKSHOP-KEY", required = false) String key,
            @RequestBody ReferenceRun request) {
        if (workshopKey.length == 0 || key == null
                || !MessageDigest.isEqual(workshopKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (request.language() == null || !LANGUAGES.contains(request.language())
                || request.sourceCode() == null || request.sourceCode().isBlank()
                || request.sourceCode().length() > MAX_CONTENT_LENGTH
                || request.input() == null || request.input().length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide supported reference code and input within 64 Ki characters");
        }
        var job = new ExecutionJob();
        job.setLanguage(request.language());
        job.setSourceCode(request.sourceCode());
        job.setInput(request.input());
        if (!availableRuns.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Workshop runners are busy");
        }
        try {
            return runners.executeSingleInput(job, MAX_CONTENT_LENGTH)
                    .whenComplete((result, failure) -> availableRuns.release());
        } catch (RuntimeException failure) {
            availableRuns.release();
            throw failure;
        }
    }

    record ReferenceRun(String language, String sourceCode, String input) {}
}
