package com.DsaDude.Execution_Submission_Service.Controller;

import com.DsaDude.Execution_Submission_Service.DTO.SubmissionUpdateDTO;
import com.DsaDude.Execution_Submission_Service.Service.SubmissionAccess;
import com.DsaDude.Execution_Submission_Service.Service.SubmissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/submissions")
public class InternalSubmissionController {
    private final SubmissionService submissions;
    private final SubmissionAccess access;

    public InternalSubmissionController(SubmissionService submissions, SubmissionAccess access) {
        this.submissions = submissions;
        this.access = access;
    }

    @PutMapping("/{submissionId}/verdict")
    public ResponseEntity<Void> updateVerdict(@PathVariable String submissionId,
            @RequestHeader(value = "X-DSACHAMP-WORKER-KEY", required = false) String key,
            @RequestBody SubmissionUpdateDTO update) {
        access.requireWorker(key);
        submissions.updateSubmission(submissionId, update);
        return ResponseEntity.noContent().build();
    }
}
