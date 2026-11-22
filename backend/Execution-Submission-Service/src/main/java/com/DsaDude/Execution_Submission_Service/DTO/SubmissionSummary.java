package com.DsaDude.Execution_Submission_Service.DTO;

import com.DsaDude.Execution_Submission_Service.Model.Submission;

import java.util.Date;

public record SubmissionSummary(String id, int userId, String questionId, String problemSlug,
                                String difficulty, String language, int codeLength, String verdict,
                                String status, long executionTime, long memoryUsed, int totalTestcases,
                                int passedTestcases, int failedTestcases, Date submissionTime) {
    public static SubmissionSummary from(Submission submission) {
        return new SubmissionSummary(submission.getId(), submission.getUserId(),
                submission.getQuestionId(), submission.getProblemSlug(), submission.getDifficulty(),
                submission.getLanguage(), submission.getCodeLength(), submission.getVerdict(),
                submission.getStatus(), submission.getExecutionTime(), submission.getMemoryUsed(),
                submission.getTotalTestcases(), submission.getPassedTestcases(),
                submission.getFailedTestcases(), submission.getSubmissionTime());
    }
}
