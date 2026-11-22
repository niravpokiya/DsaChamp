package com.DsaDude.Contest_service.standings;

import com.DsaDude.Contest_service.contest.Contest;
import com.DsaDude.Contest_service.contest.ContestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/contests/{id}/standings")
class ContestStandingsController {
    private final ContestService contests;
    private final ContestStandingsClient standings;
    private final Clock clock;

    ContestStandingsController(ContestService contests, ContestStandingsClient standings, Clock clock) {
        this.contests = contests;
        this.standings = standings;
        this.clock = clock;
    }

    @GetMapping
    StandingsPage get(@PathVariable String id, @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
        }
        Contest.Phase phase = contests.get(id).phaseAt(clock.instant());
        if (phase == Contest.Phase.SCHEDULED) return new StandingsPage(true, false, List.of());

        long offset = (long) page * size;
        var result = standings.fetch(id, offset, size + 1);
        var rows = new ArrayList<Standing>();
        for (int index = 0; index < Math.min(size, result.scores().size()); index++) {
            rows.add(new Standing(offset + index + 1, result.scores().get(index)));
        }
        return new StandingsPage(phase != Contest.Phase.ENDED || result.pending(),
                result.scores().size() > size, rows);
    }

    record Standing(long rank, ContestStandingsClient.Score score) {}
    record StandingsPage(boolean provisional, boolean hasNext, List<Standing> rows) {}
}
