package com.DsaDude.Question_service.Feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "Execution-worker-Service", contextId = "workshopExecutionClient")
public interface WorkshopExecutionClient {
    @PostMapping("/internal/workshop/run")
    ReferenceResult run(@RequestHeader("X-DSACHAMP-WORKSHOP-KEY") String key, @RequestBody ReferenceRun request);

    record ReferenceRun(String language, String sourceCode, String input) {}
    record ReferenceResult(String output, long executionTimeMs, String error, String status) {}
}
