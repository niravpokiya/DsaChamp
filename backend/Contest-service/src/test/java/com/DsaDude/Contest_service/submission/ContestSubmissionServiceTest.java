package com.DsaDude.Contest_service.submission;

import com.DsaDude.Contest_service.contest.Contest;
import com.DsaDude.Contest_service.contest.ContestProblem;
import com.DsaDude.Contest_service.contest.ContestService;
import com.DsaDude.Contest_service.registration.ContestRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContestSubmissionServiceTest {
    private static final Instant START = Instant.parse("2026-12-01T12:00:00Z");
    private static final Instant END = START.plusSeconds(1800);
    private final ContestService contests = mock(ContestService.class);
    private final ContestRegistrationService registrations = mock(ContestRegistrationService.class);
    private final ExecutionSubmissionClient judge = mock(ExecutionSubmissionClient.class);

    @Test
    void openingInstantAcceptsSubmissionUsingServerOwnedProblemMetadata() {
        when(registrations.hasJoined("round", 7)).thenReturn(true);
        var execution = new ExecutionSubmissionClient.ContestExecution(
                "round", 7, "question", "sample-problem", "EASY", 100, START, "python", "print(42)");
        var receipt = new ExecutionSubmissionClient.Receipt("job", "QUEUED");
        when(judge.submit(execution)).thenReturn(receipt);

        assertEquals(receipt, serviceAt(START).submit("round", 7, "question", "python", "print(42)"));
        verify(judge).submit(execution);
    }

    @Test
    void closingInstantRejectsSubmission() {
        assertStatus(HttpStatus.CONFLICT, serviceAt(END), "question");
        verifyNoInteractions(judge);
    }

    @Test
    void onlyRegisteredUsersCanSubmitContestProblems() {
        var service = serviceAt(END.minusMillis(1));
        assertStatus(HttpStatus.FORBIDDEN, service, "question");
        when(registrations.hasJoined("round", 7)).thenReturn(true);
        assertStatus(HttpStatus.NOT_FOUND, service, "other-question");
        verifyNoInteractions(judge);
    }

    private ContestSubmissionService serviceAt(Instant now) {
        when(contests.get("round")).thenReturn(new Contest("round", "Round", START, END,
                List.of(new ContestProblem("question", "sample-problem", "EASY", 100))));
        return new ContestSubmissionService(contests, registrations, judge, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void assertStatus(HttpStatus status, ContestSubmissionService service, String questionId) {
        var error = assertThrows(ResponseStatusException.class,
                () -> service.submit("round", 7, questionId, "python", "print(42)"));
        assertEquals(status, error.getStatusCode());
    }
}
