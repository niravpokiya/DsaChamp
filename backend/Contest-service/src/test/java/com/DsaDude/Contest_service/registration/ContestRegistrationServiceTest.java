package com.DsaDude.Contest_service.registration;

import com.DsaDude.Contest_service.contest.Contest;
import com.DsaDude.Contest_service.contest.ContestService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class ContestRegistrationServiceTest {
    @Test
    void concurrentRegistrationIsIdempotent() {
        var now = Instant.parse("2026-12-01T12:00:00Z");
        ContestService contests = mock(ContestService.class);
        ContestRegistrationRepository registrations = mock(ContestRegistrationRepository.class);
        when(contests.get("round")).thenReturn(new Contest("round", "Round", now, now.plusSeconds(60), List.of()));
        when(registrations.insert(new ContestRegistration("round:7", "round", 7, now)))
                .thenThrow(new DuplicateKeyException("Already joined"));
        var service = new ContestRegistrationService(contests, registrations, Clock.fixed(now, ZoneOffset.UTC));

        assertDoesNotThrow(() -> service.join("round", 7));
    }
}
