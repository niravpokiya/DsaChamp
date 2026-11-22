package com.DsaDude.Execution_Submission_Service.Controller;

import com.DsaDude.Execution_Submission_Service.DTO.SolvedProblemRequest;
import com.DsaDude.Execution_Submission_Service.DTO.SolvedStatsResponse;
import com.DsaDude.Execution_Submission_Service.Model.SolvedProblem;
import com.DsaDude.Execution_Submission_Service.Service.SolvedProblemService;
import com.DsaDude.Execution_Submission_Service.Service.SubmissionAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class SolvedProblemController {
    private final SolvedProblemService solvedProblemService;
    private final SubmissionAccess access;

    public SolvedProblemController(SolvedProblemService solvedProblemService, SubmissionAccess access) {
        this.solvedProblemService = solvedProblemService;
        this.access = access;
    }

    @PostMapping("/api/submissions/problem/solved/{userId}")
    public ResponseEntity<SolvedProblem> problemSolvedByUser(@RequestBody SolvedProblemRequest req, @PathVariable int userId,
            @RequestHeader(value = "X-DSACHAMP-WORKER-KEY", required = false) String key) {
        access.requireWorker(key);
        SolvedProblem solvedProblem = solvedProblemService.addSolvedByUser(req, userId);

        if (solvedProblem == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(solvedProblem);
    }

    @GetMapping("/api/solved/stats/{userId}")
    public ResponseEntity<SolvedStatsResponse> getSolvedStats(@PathVariable int userId) {
        return ResponseEntity.ok(solvedProblemService.getStats(userId));
    }

    @GetMapping("/api/solved/{userId}")
    public ResponseEntity<List<SolvedProblem>> getSolvedProblems(@PathVariable int userId) {
        return ResponseEntity.ok(solvedProblemService.getSolvedProblems(userId));
    }
}
