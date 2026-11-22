package com.DsaDude.Contest_service.contest;

import com.DsaDude.Contest_service.ContestAccess;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/contests")
class ContestController {
    private final ContestService contests;
    private final ContestAccess access;
    private final Clock clock;

    ContestController(ContestService contests, ContestAccess access, Clock clock) {
        this.contests = contests;
        this.access = access;
        this.clock = clock;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ContestView create(@RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String key,
                       @RequestHeader(value = "X-USER-ID", required = false) Integer userId,
                       @RequestHeader(value = "X-ROLE", required = false) String role,
                       @RequestBody CreateContestRequest request) {
        access.requireAdmin(key, userId, role);
        return view(contests.create(request.title(), request.startsAt(), request.endsAt(), request.problems()));
    }

    @GetMapping
    List<ContestView> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return contests.list(page, size).stream().map(this::view).toList();
    }

    @GetMapping("/{id}")
    ContestView get(@PathVariable String id) {
        return view(contests.get(id));
    }

    private ContestView view(Contest contest) {
        Contest.Phase phase = contest.phaseAt(clock.instant());
        List<ContestProblem> problems = phase == Contest.Phase.SCHEDULED ? List.of() : contest.problems();
        return new ContestView(contest.id(), contest.title(), contest.startsAt(), contest.endsAt(), phase, problems);
    }

    record CreateContestRequest(String title, Instant startsAt, Instant endsAt,
                                List<ContestService.ProblemSelection> problems) {}
    record ContestView(String id, String title, Instant startsAt, Instant endsAt,
                       Contest.Phase phase, List<ContestProblem> problems) {}
}
