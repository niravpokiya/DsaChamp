package com.DsaDude.Execution_Submission_Service.Repository;

import com.DsaDude.Execution_Submission_Service.DTO.ContestScore;
import com.DsaDude.Execution_Submission_Service.Model.Submission;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "CONTEST_TEST_MONGODB_URI", matches = ".+")
class ContestScoreRepositoryTest {
    private static final Instant START = Instant.parse("2030-01-05T10:00:00Z");

    @Test
    void ranksByPointsThenEarliestTimeAndCountsEachProblemOnlyOnce() {
        try (var client = MongoClients.create(System.getenv("CONTEST_TEST_MONGODB_URI"))) {
            var mongo = new MongoTemplate(client, "contest_test_" + UUID.randomUUID().toString().replace("-", ""));
            var submissions = new MongoRepositoryFactory(mongo).getRepository(SubmissionRepository.class);
            try {
                submissions.insert(List.of(
                        attempt("round", 1, "a", 100, 35, "COMPLETED", "ACCEPTED"),
                        attempt("round", 1, "a", 100, 5, "COMPLETED", "ACCEPTED"),
                        attempt("round", 1, "b", 100, 30, "COMPLETED", "ACCEPTED"),
                        attempt("round", 2, "a", 100, 10, "COMPLETED", "ACCEPTED"),
                        attempt("round", 2, "b", 100, 20, "COMPLETED", "ACCEPTED"),
                        attempt("round", 3, "a", 100, 1, "COMPLETED", "WRONG_ANSWER"),
                        attempt("round", 3, "b", 100, 2, "RUNNING", null),
                        attempt("other-round", 3, "a", 100, 1, "COMPLETED", "ACCEPTED")
                ));

                assertEquals(List.of(new ContestScore(2, 200, 2, START.plusSeconds(20)),
                                new ContestScore(1, 200, 2, START.plusSeconds(30))),
                        submissions.findContestScores("round", 0, 10));
                assertEquals(List.of(new ContestScore(1, 200, 2, START.plusSeconds(30))),
                        submissions.findContestScores("round", 1, 1));
                assertTrue(submissions.existsByContestIdAndStatus("round", "RUNNING"));
            } finally {
                mongo.dropCollection(Submission.class);
            }
        }
    }

    private Submission attempt(String contest, int user, String question, int points,
                               int seconds, String status, String verdict) {
        var attempt = new Submission();
        attempt.setContestId(contest);
        attempt.setUserId(user);
        attempt.setQuestionId(question);
        attempt.setContestPoints(points);
        attempt.setSubmissionTime(Date.from(START.plusSeconds(seconds)));
        attempt.setStatus(status);
        attempt.setVerdict(verdict);
        return attempt;
    }
}
