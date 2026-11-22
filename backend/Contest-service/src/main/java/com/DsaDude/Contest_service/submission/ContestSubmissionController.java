package com.DsaDude.Contest_service.submission;

import com.DsaDude.Contest_service.ContestAccess;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contests/{id}/submissions")
class ContestSubmissionController {
    private final ContestSubmissionService submissions;
    private final ContestAccess access;

    ContestSubmissionController(ContestSubmissionService submissions, ContestAccess access) {
        this.submissions = submissions;
        this.access = access;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    ExecutionSubmissionClient.Receipt submit(@PathVariable String id,
            @RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String key,
            @RequestHeader(value = "X-USER-ID", required = false) Integer userId,
            @RequestBody SubmitSolution request) {
        return submissions.submit(id, access.requireUser(key, userId),
                request.questionId(), request.language(), request.sourceCode());
    }

    record SubmitSolution(String questionId, String language, String sourceCode) {}
}
