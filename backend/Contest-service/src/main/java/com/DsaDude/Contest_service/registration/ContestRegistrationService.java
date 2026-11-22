package com.DsaDude.Contest_service.registration;

import com.DsaDude.Contest_service.contest.Contest;
import com.DsaDude.Contest_service.contest.ContestService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;

@Service
public class ContestRegistrationService {
    private final ContestService contests;
    private final ContestRegistrationRepository registrations;
    private final Clock clock;

    ContestRegistrationService(ContestService contests, ContestRegistrationRepository registrations, Clock clock) {
        this.contests = contests;
        this.registrations = registrations;
        this.clock = clock;
    }

    public void join(String contestId, int userId) {
        Contest contest = contests.get(contestId);
        var now = clock.instant();
        String id = ContestRegistration.idFor(contestId, userId);
        if (registrations.existsById(id)) return;
        if (contest.phaseAt(now) == Contest.Phase.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contest has ended");
        }
        try {
            registrations.insert(new ContestRegistration(id, contestId, userId, now));
        } catch (DuplicateKeyException alreadyJoined) {
            // Another request created the same registration.
        }
    }

    public boolean hasJoined(String contestId, int userId) {
        return registrations.existsById(ContestRegistration.idFor(contestId, userId));
    }
}
