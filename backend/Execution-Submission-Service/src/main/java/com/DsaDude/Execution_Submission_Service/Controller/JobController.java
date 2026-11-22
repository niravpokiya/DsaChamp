package com.DsaDude.Execution_Submission_Service.Controller;

import com.DsaDude.Execution_Submission_Service.DTO.ExecutionJob;
import com.DsaDude.Execution_Submission_Service.DTO.ExecutionResult;
import com.DsaDude.Execution_Submission_Service.DTO.JobResponse;
import com.DsaDude.Execution_Submission_Service.Service.ExecutionJobService;
import com.DsaDude.Execution_Submission_Service.Service.SubmissionAccess;
import com.DsaDude.Execution_Submission_Service.Service.SubmissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
@RequestMapping("api/code")
public class JobController {
    private final ExecutionJobService executionJobs;
    private final SubmissionAccess access;
    private final SubmissionService submissions;

    public JobController(ExecutionJobService executionJobs, SubmissionAccess access, SubmissionService submissions) {
        this.executionJobs = executionJobs;
        this.access = access;
        this.submissions = submissions;
    }
    // sample testcases running
    @PostMapping("/run")
    public ResponseEntity<JobResponse> queueSampleRun(@RequestBody ExecutionJob request) throws Exception {
        String jobId = UUID.randomUUID().toString();
        request.setJobId(jobId);
        request.setTypeOfJob("RUN");
        request.setUserId(0);
        request.setStatus("QUEUED");
        System.out.println(jobId);

        executionJobs.enqueueJob(request); // sending job...

        return ResponseEntity.ok(
                new JobResponse(jobId, "QUEUED")
        );
    }
    // user submitss the code here...
    @PostMapping("/submit")
    public ResponseEntity<JobResponse> enqueueExecution(@RequestBody ExecutionJob request,
            @RequestHeader(value = "X-USER-ID", required = false) Integer callerId,
            @RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String key) throws Exception {
        request.setUserId(access.requireUser(key, callerId));
        request.setTypeOfJob("SUBMIT");
        return executionJobs.enqueueExecution(request);
    }

    @GetMapping("/run/{id}")
    public ResponseEntity<ExecutionResult> getSampleRunResult(@PathVariable String id) {
        if (!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}") || submissions.submissionExists(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return executionJobs.getExecutionResult(id, false);
    }
    // poll or get submission result from here...
    @GetMapping("/{id}")
    public ResponseEntity<ExecutionResult> getOwnedExecutionResult(@PathVariable String id,
            @RequestHeader(value = "X-USER-ID", required = false) Integer callerId,
            @RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String key) {
        submissions.getOwnedSubmission(id, access.requireUser(key, callerId));
        return executionJobs.getExecutionResult(id, true);
    }
    @GetMapping("/submit/{submissionId}")
    public ResponseEntity<?> getSubmissionStatus(@PathVariable String submissionId,
            @RequestHeader(value = "X-USER-ID", required = false) Integer callerId,
            @RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String key) {
        var submission = submissions.getOwnedSubmission(submissionId, access.requireUser(key, callerId));
        // returning cache status.
        return executionJobs.getSubmissionStatus(submission);
    }
}
