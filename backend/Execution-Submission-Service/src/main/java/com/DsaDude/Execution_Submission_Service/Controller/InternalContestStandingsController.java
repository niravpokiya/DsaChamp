package com.DsaDude.Execution_Submission_Service.Controller;

import com.DsaDude.Execution_Submission_Service.DTO.ContestScore;
import com.DsaDude.Execution_Submission_Service.Repository.SubmissionRepository;
import com.DsaDude.Execution_Submission_Service.Service.ContestServiceAccess;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/internal/contest-standings")
class InternalContestStandingsController {
    private final SubmissionRepository submissions;
    private final ContestServiceAccess access;

    InternalContestStandingsController(SubmissionRepository submissions, ContestServiceAccess access) {
        this.submissions = submissions;
        this.access = access;
    }

    @GetMapping("/{id}")
    ScorePage get(@PathVariable String id, @RequestParam long offset, @RequestParam int limit,
                  @RequestHeader(value = "X-DSACHAMP-CONTEST-KEY", required = false) String key) {
        access.requireContestService(key);
        if (offset < 0 || limit < 1 || limit > 101) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid offset or limit");
        }
        boolean pending = submissions.existsByContestIdAndStatus(id, "RUNNING");
        return new ScorePage(pending, submissions.findContestScores(id, offset, limit));
    }

    record ScorePage(boolean pending, List<ContestScore> scores) {}
}
