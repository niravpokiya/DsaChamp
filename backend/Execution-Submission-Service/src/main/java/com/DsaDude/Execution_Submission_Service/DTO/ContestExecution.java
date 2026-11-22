package com.DsaDude.Execution_Submission_Service.DTO;

import java.time.Instant;

public record ContestExecution(String contestId, int userId, String questionId, String problemSlug,
                               String difficulty, int points, Instant submittedAt, String language, String sourceCode) {}
