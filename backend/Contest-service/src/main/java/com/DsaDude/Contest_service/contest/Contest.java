package com.DsaDude.Contest_service.contest;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("contests")
public record Contest(@Id String id, String title, Instant startsAt, Instant endsAt,
                      List<ContestProblem> problems) {
    public enum Phase { SCHEDULED, RUNNING, ENDED }

    public Phase phaseAt(Instant time) {
        if (time.isBefore(startsAt)) return Phase.SCHEDULED;
        return time.isBefore(endsAt) ? Phase.RUNNING : Phase.ENDED;
    }
}
