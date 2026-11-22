package com.DsaDude.Question_service.Controller;

import com.DsaDude.Question_service.Feign.WorkshopExecutionClient;
import com.DsaDude.Question_service.Helpers.AdminOnly;
import com.DsaDude.Question_service.Services.ProblemWorkshopService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/question/{problemId}/workshop")
class ProblemWorkshopController {
    private final ProblemWorkshopService workshop;

    ProblemWorkshopController(ProblemWorkshopService workshop) {
        this.workshop = workshop;
    }

    @PostMapping("/reference-runs")
    @AdminOnly
    WorkshopExecutionClient.ReferenceResult generateOutput(@PathVariable String problemId,
            @RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String gatewayKey,
            @RequestBody WorkshopExecutionClient.ReferenceRun request) {
        return workshop.generateOutput(problemId, gatewayKey, request);
    }

    @PostMapping("/testcases")
    @AdminOnly
    TestcaseResult addTestcase(@PathVariable String problemId,
            @RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String gatewayKey,
            @RequestBody TestcasePair request) {
        return new TestcaseResult(workshop.addTestcase(problemId, gatewayKey, request.input(), request.output()));
    }

    record TestcasePair(String input, String output) {}
    record TestcaseResult(boolean added) {}

    @PostMapping("/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AdminOnly
    void publish(@PathVariable String problemId,
            @RequestHeader(value = "X-DSACHAMP-GATEWAY-KEY", required = false) String gatewayKey) {
        workshop.publish(problemId, gatewayKey);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> workshopError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(exception.getBody());
    }
}
