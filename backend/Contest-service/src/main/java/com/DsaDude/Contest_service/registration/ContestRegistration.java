package com.DsaDude.Contest_service.registration;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("contest_registrations")
record ContestRegistration(@Id String id, String contestId, int userId, Instant joinedAt) {
    static String idFor(String contestId, int userId) {
        return contestId + ":" + userId;
    }
}
