package com.DsaDude.Execution_Submission_Service.DTO;

import java.time.Instant;

public record ContestScore(int userId, int points, int solved, Instant scoreReachedAt) {}
