package com.DsaDude.Contest_service.standings;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Component
class ContestStandingsClient {
    private final RestClient client;

    ContestStandingsClient(@Qualifier("contestServiceClient") RestClient.Builder builder,
                           @Value("${DSACHAMP_CONTEST_KEY:}") String contestKey) {
        client = builder.clone().baseUrl("http://Execution-Submission-Service")
                .defaultHeader("X-DSACHAMP-CONTEST-KEY", contestKey).build();
    }

    ScorePage fetch(String contestId, long offset, int limit) {
        try {
            ScorePage page = client.get().uri(uri -> uri.path("/internal/contest-standings/{id}")
                            .queryParam("offset", offset).queryParam("limit", limit).build(contestId))
                    .retrieve().body(ScorePage.class);
            if (page == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Judge returned no standings");
            }
            return page;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Contest scores are unavailable", exception);
        }
    }

    record ScorePage(boolean pending, List<Score> scores) {}
    record Score(int userId, int points, int solved, Instant scoreReachedAt) {}
}
