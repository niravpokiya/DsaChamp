package com.DsaDude.Execution_Submission_Service.Controller;

import com.DsaDude.Execution_Submission_Service.DTO.ContestExecution;
import com.DsaDude.Execution_Submission_Service.DTO.JobResponse;
import com.DsaDude.Execution_Submission_Service.Service.ContestServiceAccess;
import com.DsaDude.Execution_Submission_Service.Service.ExecutionJobService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/contest-submissions")
class InternalContestSubmissionController {
    private final ContestServiceAccess access;
    private final ExecutionJobService jobs;

    InternalContestSubmissionController(ContestServiceAccess access, ExecutionJobService jobs) {
        this.access = access;
        this.jobs = jobs;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    JobResponse submit(@RequestHeader(value = "X-DSACHAMP-CONTEST-KEY", required = false) String key,
                       @RequestBody ContestExecution submission) {
        access.requireContestService(key);
        return jobs.enqueueContestSubmission(submission);
    }
}
