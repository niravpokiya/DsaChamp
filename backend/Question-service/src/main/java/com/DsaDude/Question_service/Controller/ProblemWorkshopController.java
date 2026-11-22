package com.DsaDude.Question_service.Controller;

import com.DsaDude.Question_service.Feign.WorkshopExecutionClient;
import com.DsaDude.Question_service.Helpers.AdminOnly;
import com.DsaDude.Question_service.Services.ProblemWorkshopService;
import org.springframework.web.bind.annotation.*;

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
}
