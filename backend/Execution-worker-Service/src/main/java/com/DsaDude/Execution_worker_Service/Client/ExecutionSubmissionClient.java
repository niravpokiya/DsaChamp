package com.DsaDude.Execution_worker_Service.Client;

import com.DsaDude.Execution_worker_Service.DTO.SubmissionUpdate;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "Execution-Submission-Service")
@Component
public interface ExecutionSubmissionClient {
    @PutMapping("/internal/submissions/{submissionId}/verdict")
    public ResponseEntity<?> updateSubmission(
            @PathVariable String submissionId,
            @RequestHeader("X-DSACHAMP-WORKER-KEY") String workerKey,
            @RequestBody SubmissionUpdate dto
    );
}
