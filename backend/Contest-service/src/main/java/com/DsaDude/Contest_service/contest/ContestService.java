package com.DsaDude.Contest_service.contest;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ContestService {
    private final ContestRepository contests;
    private final Clock clock;
    private final ContestProblemCatalog problemCatalog;

    ContestService(ContestRepository contests, Clock clock, ContestProblemCatalog problemCatalog) {
        this.contests = contests;
        this.clock = clock;
        this.problemCatalog = problemCatalog;
    }

    public Contest create(String title, Instant startsAt, Instant endsAt, List<ProblemSelection> problems) {
        if (title == null || title.isBlank() || title.length() > 120
                || startsAt == null || endsAt == null || !startsAt.isAfter(clock.instant())
                || !endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a title and a future contest window");
        }
        if (problems == null || problems.isEmpty() || problems.size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A contest needs between 1 and 20 problems");
        }
        var questionIds = new HashSet<String>();
        var resolvedProblems = new ArrayList<ContestProblem>(problems.size());
        for (ProblemSelection selection : problems) {
            if (selection == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing problem selection");
            ContestProblem problem = problemCatalog.resolve(selection.slug(), selection.points());
            if (!questionIds.add(problem.questionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contest problems must be distinct");
            }
            resolvedProblems.add(problem);
        }
        if (!startsAt.isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contest start must still be in the future");
        }
        return contests.insert(new Contest(UUID.randomUUID().toString(), title.trim(), startsAt, endsAt, List.copyOf(resolvedProblems)));
    }

    public record ProblemSelection(String slug, int points) {}

    public Contest get(String id) {
        return contests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found"));
    }

    public List<Contest> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
        }
        return contests.findAll(PageRequest.of(page, size, Sort.by("startsAt", "id").descending())).getContent();
    }
}
