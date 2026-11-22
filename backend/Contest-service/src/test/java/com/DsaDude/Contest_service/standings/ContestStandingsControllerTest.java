package com.DsaDude.Contest_service.standings;

import com.DsaDude.Contest_service.contest.Contest;
import com.DsaDude.Contest_service.contest.ContestService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContestStandingsControllerTest {
    @Test
    void standingsKeepRankAcrossPagesAndRemainProvisionalWhileJobsArePending() {
        var end = Instant.parse("2026-12-01T12:30:00Z");
        ContestService contests = mock(ContestService.class);
        ContestStandingsClient scores = mock(ContestStandingsClient.class);
        when(contests.get("round")).thenReturn(new Contest("round", "Round", end.minusSeconds(1800), end, List.of()));
        when(scores.fetch("round", 2, 3)).thenReturn(new ContestStandingsClient.ScorePage(true, List.of(
                new ContestStandingsClient.Score(3, 100, 1, end.minusSeconds(20)),
                new ContestStandingsClient.Score(4, 100, 1, end.minusSeconds(10)),
                new ContestStandingsClient.Score(5, 50, 1, end.minusSeconds(30)))));
        var controller = new ContestStandingsController(contests, scores, Clock.fixed(end, ZoneOffset.UTC));

        var page = controller.get("round", 1, 2);
        assertTrue(page.provisional());
        assertTrue(page.hasNext());
        assertEquals(List.of(3L, 4L), page.rows().stream().map(ContestStandingsController.Standing::rank).toList());
    }
}
