package com.DsaDude.Execution_Submission_Service.Service;

import com.DsaDude.Execution_Submission_Service.DTO.ExecutionJob;
import com.DsaDude.Execution_Submission_Service.DTO.ExecutionResult;
import com.DsaDude.Execution_Submission_Service.DTO.JobResponse;
import com.DsaDude.Execution_Submission_Service.Model.Submission;
import com.DsaDude.Execution_Submission_Service.Repository.SubmissionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ExecutionJobService {
    private final KafkaTemplate<String, ExecutionJob> kafkaTemplate;
    private final SubmissionRepository submissionRepository;
    private final RedisService redisService;
    private final SubmissionService submissionService;

    public ExecutionJobService(KafkaTemplate<String, ExecutionJob> kafkaTemplate,
                       SubmissionRepository submissionRepository, RedisService redisService,
                       SubmissionService submissionService) {
        this.kafkaTemplate = kafkaTemplate;
        this.submissionRepository = submissionRepository;
        this.redisService = redisService;
        this.submissionService = submissionService;
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
}
