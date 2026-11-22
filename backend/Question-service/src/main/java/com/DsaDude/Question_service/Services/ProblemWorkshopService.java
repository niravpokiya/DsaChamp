package com.DsaDude.Question_service.Services;

import com.DsaDude.Question_service.Feign.WorkshopExecutionClient;
import com.DsaDude.Question_service.Model.Question;
import com.DsaDude.Question_service.Repository.QuestionRepository;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class ProblemWorkshopService {
    private final QuestionRepository questions;
    private final WorkshopExecutionClient worker;
    private final byte[] gatewayKey;
    private final String workshopKey;

    public ProblemWorkshopService(QuestionRepository questions, WorkshopExecutionClient worker,
            @Value("${DSACHAMP_GATEWAY_KEY:}") String gatewayKey,
            @Value("${DSACHAMP_WORKSHOP_KEY:}") String workshopKey) {
        this.questions = questions;
        this.worker = worker;
        this.gatewayKey = gatewayKey.getBytes(StandardCharsets.UTF_8);
        this.workshopKey = workshopKey;
    }

    public WorkshopExecutionClient.ReferenceResult generateOutput(String id, String key,
            WorkshopExecutionClient.ReferenceRun request) {
        requireStaticDraft(id, key);
        if (workshopKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Reference execution is not configured");
        }
        try {
            return worker.run(workshopKey, request);
        } catch (FeignException exception) {
            if (exception.status() == 400) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reference code or testcase input");
            }
            if (exception.status() == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Workshop runners are busy");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Reference runner is unavailable");
        }
    }

    private Question requireStaticDraft(String id, String key) {
        if (gatewayKey.length == 0 || key == null
                || !MessageDigest.isEqual(gatewayKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Question question = questions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found"));
        if (question.isPublic() || !question.isStaticSolution()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Use a saved draft with exact output comparison");
        }
        return question;
    }
}
