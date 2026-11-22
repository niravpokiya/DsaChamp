package com.DsaDude.Contest_service.contest;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ContestService {
    private final ContestRepository contests;
    private final Clock clock;

    public ContestService(ContestRepository contests, Clock clock) {
        this.contests = contests;
        this.clock = clock;
    }

    public Contest create(String title, Instant startsAt, Instant endsAt, List<ContestProblem> problems) {
        if (title == null || title.isBlank() || title.length() > 120
                || startsAt == null || endsAt == null || !startsAt.isAfter(clock.instant())
                || !endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a title and a future contest window");
        }
        if (problems == null || problems.isEmpty() || problems.size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A contest needs between 1 and 20 problems");
        }
        var questionIds = new HashSet<String>();
        for (ContestProblem problem : problems) {
            if (problem == null || problem.questionId() == null || problem.questionId().isBlank()
                    || problem.slug() == null || !problem.slug().matches("[a-z0-9]+(-[a-z0-9]+)*")
                    || problem.points() < 1 || problem.points() > 1000
                    || !questionIds.add(problem.questionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide distinct problems with valid points");
            }
        }
        return contests.insert(new Contest(UUID.randomUUID().toString(), title.trim(), startsAt, endsAt, List.copyOf(problems)));
    }

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
