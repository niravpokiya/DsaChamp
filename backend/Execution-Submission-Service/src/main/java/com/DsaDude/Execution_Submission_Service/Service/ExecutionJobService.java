package com.DsaDude.Execution_Submission_Service.Service;

import com.DsaDude.Execution_Submission_Service.DTO.ExecutionJob;
import com.DsaDude.Execution_Submission_Service.DTO.ContestExecution;
import com.DsaDude.Execution_Submission_Service.DTO.ExecutionResult;
import com.DsaDude.Execution_Submission_Service.DTO.JobResponse;
import com.DsaDude.Execution_Submission_Service.Model.Submission;
import com.DsaDude.Execution_Submission_Service.Repository.SubmissionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ExecutionJobService {
    private static final Logger log = LoggerFactory.getLogger(ExecutionJobService.class);
    private final KafkaTemplate<String, ExecutionJob> kafkaTemplate;
    private final SubmissionRepository submissionRepository;
    private final RedisService redisService;
    private final SubmissionService submissionService;
    private final MongoTemplate mongo;

    public ExecutionJobService(KafkaTemplate<String, ExecutionJob> kafkaTemplate,
                       SubmissionRepository submissionRepository, RedisService redisService,
                       SubmissionService submissionService, MongoTemplate mongo) {
        this.kafkaTemplate = kafkaTemplate;
        this.submissionRepository = submissionRepository;
        this.redisService = redisService;
        this.submissionService = submissionService;
        this.mongo = mongo;
    }

    public JobResponse enqueueContestSubmission(ContestExecution request) {
        Submission submission = new Submission();
        submission.setContestId(request.contestId());
        submission.setContestPoints(request.points());
        submission.setUserId(request.userId());
        submission.setQuestionId(request.questionId());
        submission.setProblemSlug(request.problemSlug());
        submission.setDifficulty(request.difficulty());
        submission.setLanguage(request.language());
        submission.setSourceCode(request.sourceCode());
        submission.setSubmissionTime(Date.from(request.submittedAt()));
        submission.setStatus("RUNNING");
        String id = submissionRepository.insert(submission).getId();

        ExecutionJob job = new ExecutionJob();
        job.setJobId(id);
        job.setUserId(request.userId());
        job.setQuestionId(request.questionId());
        job.setProblemSlug(request.problemSlug());
        job.setDifficulty(request.difficulty());
        job.setLanguage(request.language());
        job.setSourceCode(request.sourceCode());
        job.setTypeOfJob("SUBMIT");
        job.setStatus("QUEUED");
        try {
            redisService.markPending(id, job.getSourceCode(), job.getLanguage(), 0, 0);
            kafkaTemplate.send("execution-jobs", id, job).whenComplete((result, failure) -> {
                if (failure != null) markContestDispatchFailed(id, failure);
            });
        } catch (RuntimeException failure) {
            markContestDispatchFailed(id, failure);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Judge queue is unavailable", failure);
        }
        return new JobResponse(id, "QUEUED");
    }

    private void markContestDispatchFailed(String id, Throwable failure) {
        log.warn("Could not queue contest submission {}", id, failure);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("status").is("RUNNING")),
                new Update().set("status", "COMPLETED").set("verdict", "SYSTEM_ERROR"), Submission.class);
    }

    public void enqueueJob(ExecutionJob job) throws Exception {
        redisService.markPending(job.getJobId(), job.getSourceCode(), job.getLanguage(), 0,0);
        System.out.println("Incoming job : " + job.getProblemSlug() + " " + job.getTypeOfJob());
        kafkaTemplate.send("execution-jobs", job.getJobId(), job);
    }

    public ResponseEntity<JobResponse> enqueueExecution(ExecutionJob request) throws Exception {

        String jobId;

        if ("RUN".equals(request.getTypeOfJob())) {

            // Sample run → no DB persistence
            jobId = UUID.randomUUID().toString();

        } else if ("SUBMIT".equals(request.getTypeOfJob())) {

            Submission submission = new Submission();

            submission.setUserId(request.getUserId());
            submission.setQuestionId(request.getQuestionId());
            submission.setProblemSlug(request.getProblemSlug());
            submission.setDifficulty(normalizeDifficulty(request.getDifficulty()));
            submission.setSourceCode(request.getSourceCode());
            submission.setLanguage(request.getLanguage());

            submission.setStatus("RUNNING");
            submission.setSubmissionTime(new Date());

            submission = submissionRepository.save(submission);

            jobId = submission.getId();
        } else {
            throw new IllegalArgumentException("Unknown job type: " + request.getTypeOfJob());
        }

        request.setJobId(jobId);
        request.setStatus("QUEUED");

        enqueueJob(request);

        return ResponseEntity.ok(
                new JobResponse(jobId, "QUEUED")
        );
    }

    private String normalizeDifficulty(String difficulty) {
        return difficulty == null ? null : difficulty.trim().toUpperCase();
    }

    public ResponseEntity<ExecutionResult> getExecutionResult(String id, boolean submission) {
        System.out.println("Getting result for job: " + id);
        Map<Object, Object> data = redisService.getExecution(id);

        ExecutionResult res = new ExecutionResult();

        // FAST PATH (Redis) - For active jobs and recent results
        if (data != null && !data.isEmpty()) {
            
            String status = (String) data.get("status");
            String output = (String) data.getOrDefault("output", "");
            String verdict = (String) data.getOrDefault("verdict", "");
            String error = (String) data.getOrDefault("error", "");

            // Map status to appropriate verdict
            if ("COMPLETED".equals(status) || "SUCCESS".equals(status)) {
                res.setStatus("COMPLETED");
                res.setVerdict(verdict.isEmpty() ? "SUCCESS" : verdict);
            } else if ("FAILED".equals(status) || "ERROR".equals(status)) {
                res.setStatus("FAILED");
                res.setVerdict(verdict.isEmpty() ? "ERROR" : verdict);
            } else {
                res.setStatus(status);
                res.setVerdict(verdict);
            }

            res.setOutput(submission ? "" : output);
            res.setError(!submission || "COMPILE_ERROR".equals(verdict) ? error : "");

            Object time = data.get("executionTimeMs");
            if (time != null) {
                res.setExecutionTimeMs(Long.parseLong(time.toString()));
            }
            
            System.out.println("Redis result found - Status: " + res.getStatus() + ", Verdict: " + res.getVerdict());
            return ResponseEntity.ok(res);
        }

        // FALLBACK (MongoDB) - For completed submissions
        try {
            Submission sub = submission ? submissionService.getSubmissionById(id) : null;
            
            if (sub != null) {
                // Map MongoDB submission to ExecutionResult
                res.setStatus("COMPLETED");
                res.setVerdict(sub.getVerdict());
                res.setExecutionTimeMs(sub.getExecutionTime());
                res.setError("COMPILE_ERROR".equals(sub.getVerdict()) ? sub.getErrorMessage() : "");
                res.setOutput(""); // Output not stored in MongoDB for submissions
                
                // Add test case information if available
                if (sub.getTotalTestcases() > 0) {
                    String resultInfo = String.format("Passed: %d/%d test cases", 
                        sub.getPassedTestcases(), sub.getTotalTestcases());
                    res.setOutput(resultInfo);
                }
                
                System.out.println("MongoDB result found - Verdict: " + res.getVerdict());
                return ResponseEntity.ok(res);
            }
        } catch (Exception e) {
            System.err.println("Error fetching from MongoDB: " + e.getMessage());
        }

        // NO RESULT FOUND
        res.setStatus("NOT_FOUND");
        res.setVerdict("PENDING");
        res.setError("Result not found");
        res.setExecutionTimeMs(0);
        
        System.out.println("No result found for job: " + id);
        return ResponseEntity.ok(res);
    }

    public ResponseEntity<?> getCachedSubmissionStatus(String submissionId) {
        Map<Object, Object> result = new HashMap<>(redisService.getExecution(submissionId));
        result.remove("output");
        if (!"COMPILE_ERROR".equals(result.get("verdict"))) {
            result.remove("error");
        }
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<?> getSubmissionStatus(Submission submission) {
        if (submission.getContestId() == null) {
            return getCachedSubmissionStatus(submission.getId());
        }
        return ResponseEntity.ok(Map.of(
                "status", submission.getStatus(),
                "verdict", submission.getVerdict() == null ? "PENDING" : submission.getVerdict(),
                "passed", submission.getPassedTestcases(),
                "total", submission.getTotalTestcases(),
                "executionTimeMs", submission.getExecutionTime(),
                "error", "COMPILE_ERROR".equals(submission.getVerdict()) && submission.getErrorMessage() != null
                        ? submission.getErrorMessage() : ""
        ));
    }
}
