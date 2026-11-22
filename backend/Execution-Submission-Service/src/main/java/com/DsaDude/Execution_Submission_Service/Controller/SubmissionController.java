package com.DsaDude.Execution_Submission_Service.Controller;

import com.DsaDude.Execution_Submission_Service.DTO.SubmissionSummary;
import com.DsaDude.Execution_Submission_Service.Model.Submission;
import com.DsaDude.Execution_Submission_Service.Service.SubmissionAccess;
import com.DsaDude.Execution_Submission_Service.Service.SubmissionService;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {
    private final SubmissionService submissionService;
    private final SubmissionAccess access;

    public SubmissionController(SubmissionService submissionService, SubmissionAccess access) {
        this.submissionService = submissionService;
        this.access = access;
    }

    // getting all submissions done by perticular user...
    @GetMapping("/all-submissions/{userId}")
    public List<SubmissionSummary> getAllSubmissionsDoneByUser(@PathVariable int userId,
            @RequestHeader(value = "X-USER-ID", required = false) Integer callerId,
            @RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String key) {
        return submissionService.getUserSubmissions(access.requireOwner(key, callerId, userId))
                .stream().map(SubmissionSummary::from).toList();
    }

    @GetMapping("/problem/{slug}")
    public List<SubmissionSummary> getSubmissionsByProblemSlug(@PathVariable String slug,
            @RequestHeader(value = "X-USER-ID", required = false) Integer callerId,
            @RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String key) {
        return submissionService.getSubmissionsByProblemSlug(slug, access.requireUser(key, callerId))
                .stream().map(SubmissionSummary::from).toList();
    }

    @GetMapping("/{userId}/count")
    public int getSubmissionsCountByUser(@PathVariable int userId,
            @RequestHeader(value = "X-USER-ID", required = false) Integer callerId,
            @RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String key) {
        return submissionService.countUserSubmissions(access.requireOwner(key, callerId, userId));
    }

    @GetMapping("/heatmap")
    public Map<String, Integer> getHeatmap(@RequestParam int userId,
            @RequestHeader(value = "X-USER-ID", required = false) Integer callerId,
            @RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String key) {
        return submissionService.getHeatMap(access.requireOwner(key, callerId, userId));
    }

    @GetMapping("/{submissionId}")
    public SubmissionDetail getSubmissionById(@PathVariable String submissionId,
            @RequestHeader(value = "X-USER-ID", required = false) Integer callerId,
            @RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String key) {
        Submission submission = submissionService.getOwnedSubmission(submissionId, access.requireUser(key, callerId));
        String error = "COMPILE_ERROR".equals(submission.getVerdict()) ? submission.getErrorMessage() : null;
        return new SubmissionDetail(SubmissionSummary.from(submission), submission.getSourceCode(), error);
    }

    public record SubmissionDetail(@JsonUnwrapped SubmissionSummary summary, String sourceCode, String errorMessage) {}
}
