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
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;

@Service
public class ProblemWorkshopService {
    private final QuestionRepository questions;
    private final WorkshopExecutionClient worker;
    private final TestcaseService testcases;
    private final byte[] gatewayKey;
    private final String workshopKey;

    public ProblemWorkshopService(QuestionRepository questions, WorkshopExecutionClient worker, TestcaseService testcases,
            @Value("${DSACHAMP_GATEWAY_KEY:}") String gatewayKey,
            @Value("${DSACHAMP_WORKSHOP_KEY:}") String workshopKey) {
        this.questions = questions;
        this.worker = worker;
        this.testcases = testcases;
        this.gatewayKey = gatewayKey.getBytes(StandardCharsets.UTF_8);
        this.workshopKey = workshopKey;
    }

    public synchronized boolean addTestcase(String id, String key, String input, String output) {
        Question question = requireStaticDraft(id, key);
        if (input == null || output == null || input.length() > 65_536 || output.length() > 65_536) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Input and output must be within 64 Ki characters");
        }
        String slug = resolveJudgeSlug(question);
        try {
            return testcases.appendWorkshopTestcase(slug, input, output);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the hidden testcase", exception);
        }
    }

    public synchronized void publish(String id, String key) {
        Question question = requireStaticDraft(id, key);
        String slug = resolveJudgeSlug(question);
        if (question.getDescription() == null || question.getDescription().isBlank()
                || question.getDifficulty() == null || question.getExamples() == null || question.getExamples().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Add a statement, difficulty, and at least one public example first");
        }
        synchronized (testcases) {
            try {
                if (!testcases.hasCompleteHiddenTests(slug)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Add a complete hidden testcase bundle first");
                }
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not validate the testcase bundle", exception);
            }
            question.setPublic(true);
            questions.save(question);
        }
    }

    private String resolveJudgeSlug(Question question) {
        String slug = question.getTitle() == null ? "" : question.getTitle().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        boolean reachable = !slug.isBlank() && questions.findByTitleIgnoreCase(slug.replace('-', ' '))
                .filter(found -> question.getId().equals(found.getId())).isPresent();
        if (!reachable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Save a title that resolves to this exact problem");
        }
        return slug;
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
