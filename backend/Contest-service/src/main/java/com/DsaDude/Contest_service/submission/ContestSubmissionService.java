package com.DsaDude.Contest_service.submission;

import com.DsaDude.Contest_service.contest.Contest;
import com.DsaDude.Contest_service.contest.ContestService;
import com.DsaDude.Contest_service.registration.ContestRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Set;

@Service
class ContestSubmissionService {
    private static final Set<String> LANGUAGES = Set.of("cpp", "java", "python");
    private final ContestService contests;
    private final ContestRegistrationService registrations;
    private final ExecutionSubmissionClient judge;
    private final Clock clock;

    ContestSubmissionService(ContestService contests, ContestRegistrationService registrations,
                             ExecutionSubmissionClient judge, Clock clock) {
        this.contests = contests;
        this.registrations = registrations;
        this.judge = judge;
        this.clock = clock;
    }

    ExecutionSubmissionClient.Receipt submit(String contestId, int userId, String questionId,
                                             String language, String sourceCode) {
        Contest contest = contests.get(contestId);
        var submittedAt = clock.instant();
        if (contest.phaseAt(submittedAt) != Contest.Phase.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contest submissions are closed");
        }
        if (!registrations.hasJoined(contestId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join the contest before submitting");
        }
        if (language == null || !LANGUAGES.contains(language) || sourceCode == null
                || sourceCode.isBlank() || sourceCode.length() > 65_536) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide valid code and a supported language");
        }
        var problem = contest.problems().stream().filter(entry -> entry.questionId().equals(questionId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem is not in this contest"));
        return judge.submit(new ExecutionSubmissionClient.ContestExecution(contestId, userId, problem.questionId(),
                problem.slug(), problem.difficulty(), problem.points(), submittedAt, language, sourceCode));
    }
}
