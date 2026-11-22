package com.DsaDude.Contest_service.submission;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Component
public class ExecutionSubmissionClient {
    private final RestClient client;

    public ExecutionSubmissionClient(@Qualifier("contestServiceClient") RestClient.Builder builder,
                                      @Value("${DSACHAMP_CONTEST_KEY:}") String contestKey) {
        client = builder.clone().baseUrl("http://Execution-Submission-Service")
                .defaultHeader("X-DSACHAMP-CONTEST-KEY", contestKey).build();
    }

    public Receipt submit(ContestExecution execution) {
        try {
            Receipt receipt = client.post().uri("/internal/contest-submissions")
                    .body(execution).retrieve().body(Receipt.class);
            if (receipt == null || receipt.jobId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Judge returned no submission ID");
            }
            return receipt;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Submission Service could not accept the request", exception);
        }
    }

    public record ContestExecution(String contestId, int userId, String questionId, String problemSlug,
                                   String difficulty, int points, Instant submittedAt, String language, String sourceCode) {}
    public record Receipt(String jobId, String status) {}
}
