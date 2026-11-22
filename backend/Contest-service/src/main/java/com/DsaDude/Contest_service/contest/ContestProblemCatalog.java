package com.DsaDude.Contest_service.contest;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
class ContestProblemCatalog {
    private final RestClient client;

    ContestProblemCatalog(@Qualifier("contestServiceClient") RestClient.Builder client) {
        this.client = client.clone().baseUrl("http://question-service").build();
    }

    ContestProblem resolve(String slug, int points) {
        if (slug == null || slug.length() > 160 || !slug.matches("[a-z0-9]+(-[a-z0-9]+)*")
                || points < 1 || points > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a valid problem slug and points");
        }
        try {
            QuestionResponse response = client.get().uri("/api/question/{slug}", slug).retrieve().body(QuestionResponse.class);
            Question question = response == null ? null : response.data();
            if (question == null || !question.published() || question.id() == null || question.difficulty() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contest problems must be published");
            }
            return new ContestProblem(question.id(), slug, question.difficulty(), points);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contest problem does not exist");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Question Service rejected the lookup", exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Question Service is unavailable", exception);
        }
    }

    record QuestionResponse(Question data) {}
    record Question(String id, String difficulty, @JsonAlias({"public", "isPublic"}) boolean published) {}
}
