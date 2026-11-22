package com.DsaDude.Execution_worker_Service.Service;

import com.DsaDude.Execution_worker_Service.Client.ExecutionSubmissionClient;
import com.DsaDude.Execution_worker_Service.Client.QuestionServiceClient;
import com.DsaDude.Execution_worker_Service.Client.QuestionServiceClient.QuestionDTO;
import com.DsaDude.Execution_worker_Service.DTO.ExecutionJob;
import com.DsaDude.Execution_worker_Service.DTO.ExecutionResult;
import com.DsaDude.Execution_worker_Service.DTO.SubmissionUpdate;
import com.DsaDude.Execution_worker_Service.DTO.TestcaseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class CodeExecutionConsumer {

    @Autowired
    private ContainerPoolManager containerPoolManager;
    @Autowired
    private QuestionServiceClient questionServiceClient;
    @Autowired
    private RedisService redisService;
    @Autowired
    private ExecutionSubmissionClient submissionClient;

    @Value("${DSACHAMP_WORKER_KEY:}")
    private String workerKey;

    @Value("${testcase.storage.path:testcase}")
    private String testcaseStoragePath;

    @KafkaListener(topics = "execution-jobs", groupId = "executor-group")
    public void consumeExecutionJob(ExecutionJob job) {
        try {
            if (job == null) {
                log.warn("Received null execution job");
                return;
            }

            if ("RUN".equals(job.getTypeOfJob())) {
                runSampleOrCustomInput(job);
            } else if ("SUBMIT".equals(job.getTypeOfJob())) {
                judgeSubmission(job);
            } else {
                log.warn("Unknown job type: {} for job: {}", job.getTypeOfJob(), job.getJobId());
                markJobFailure(job, "SYSTEM_ERROR", "Unknown job type: " + job.getTypeOfJob());
            }
        } catch (Exception e) {
            log.error("Failed to consumeExecutionJob execution job: {}", job != null ? job.getJobId() : "null", e);
            markJobFailure(job, "SYSTEM_ERROR", e.getMessage());
        }
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read file: " + path);
        }
    }

    private boolean outputsMatch(String expected, String actual) {
        if (expected == null && actual == null) return true;
        if (expected == null || actual == null) return false;
        return normalizeOutput(expected).equals(normalizeOutput(actual));
    }

    private String normalizeOutput(String value) {
        String normalized = value
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();

        String[] lines = normalized.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = lines[i].replaceAll("\\s+$", "");
        }
        return String.join("\n", lines).trim();
    }

    private void judgeSubmission(ExecutionJob job) {
        String jobId = job.getJobId();
        try {
            redisService.markRunning(jobId, job.getSourceCode(), job.getLanguage(), 0, 0);

            String slug = job.getProblemSlug();
            if (slug == null || slug.isBlank()) {
                failSubmission(jobId, job, "SYSTEM_ERROR", "Problem slug is missing");
                return;
            }

            var questionResponse = questionServiceClient.getQuestionBySlug(slug);
            var questionDTO = questionResponse != null ? questionResponse.getData() : null;
            if (questionDTO == null) {
                failSubmission(jobId, job, "SYSTEM_ERROR", "Question metadata not found");
                return;
            }

            Path problemDir = Path.of(testcaseStoragePath).toAbsolutePath().normalize().resolve(slug);
            if (!Files.exists(problemDir)) {
                failSubmission(jobId, job, "SYSTEM_ERROR", "Testcases not found");
                return;
            }

            try (var stream = Files.list(problemDir)) {
                List<Path> inputFiles = stream
                        .filter(path -> path.getFileName().toString().endsWith(".in"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();

                if (inputFiles.isEmpty()) {
                    failSubmission(jobId, job, "SYSTEM_ERROR", "No testcase input files found");
                    return;
                }

                List<String> inputs = new ArrayList<>();
                for (Path inputFile : inputFiles) {
                    inputs.add(readFile(inputFile));
                }

                List<String> expectedOutputs = loadStaticExpectedOutputs(inputFiles, questionDTO, problemDir);
                CompletableFuture<List<ExecutionResult>> executionFuture = executeSubmissionInputs(job, inputs, questionDTO, expectedOutputs);

                executionFuture
                        .thenCompose(results -> evaluateTestcaseResults(inputFiles, inputs, results, questionDTO, expectedOutputs, job, jobId))
                        .thenAccept(testcaseResults -> publishSubmissionVerdict(testcaseResults, jobId, job))
                        .exceptionally(ex -> {
                            failSubmission(jobId, job, "SYSTEM_ERROR", ex.getMessage());
                            return null;
                        });
            }

        } catch (Exception e) {
            failSubmission(jobId, job, "SYSTEM_ERROR", e.getMessage());
        }
    }

    private List<String> loadStaticExpectedOutputs(
            List<Path> inputFiles,
            QuestionDTO questionDTO,
            Path problemDir) {
        if (!questionDTO.isStaticSolution()) {
            return List.of();
        }

        List<String> expectedOutputs = new ArrayList<>();
        for (Path inputFile : inputFiles) {
            expectedOutputs.add(readFile(problemDir.resolve(inputFile.getFileName().toString().replace(".in", ".out"))));
        }
        return expectedOutputs;
    }

    private CompletableFuture<List<ExecutionResult>> executeSubmissionInputs(
            ExecutionJob job,
            List<String> inputs,
            QuestionDTO questionDTO,
            List<String> expectedOutputs) {
        if (!questionDTO.isStaticSolution()) {
            return containerPoolManager.executeTestcaseBatch(job, inputs);
        }

        return containerPoolManager.executeTestcaseBatch(
                job,
                inputs,
                (index, result) -> shouldStopAfterStaticResult(index, result, expectedOutputs)
        );
    }

    private boolean shouldStopAfterStaticResult(int index, ExecutionResult result, List<String> expectedOutputs) {
        if (result == null || !"SUCCESS".equals(result.getStatus())) {
            return false;
        }

        return !outputsMatch(expectedOutputs.get(index), result.getOutput());
    }

    private boolean shouldStopAfterCheckerResult(int index, ExecutionResult result) {
        if (result == null || !"SUCCESS".equals(result.getStatus())) {
            return false;
        }

        return !isCheckerAccepted(result.getOutput());
    }

    private CompletableFuture<List<TestcaseResult>> evaluateTestcaseResults(
            List<Path> inputFiles,
            List<String> inputs,
            List<ExecutionResult> results,
            QuestionDTO questionDTO,
            List<String> expectedOutputs,
            ExecutionJob job,
            String jobId
    ) {
        List<TestcaseResult> testcaseResults = new ArrayList<>();
        List<PendingCheckerTestcase> pendingCheckerTestcases = new ArrayList<>();

        for (int i = 0; i < inputFiles.size(); i++) {
            int currentTestcase = i + 1;
            Path inputFile = inputFiles.get(i);
            String input = inputs.get(i);
            ExecutionResult result = i < results.size()
                    ? results.get(i)
                    : new ExecutionResult("", 0, "Missing execution result", "SYSTEM_ERROR");

            if (!"SUCCESS".equals(result.getStatus())) {
                testcaseResults.add(buildTestcaseResult(currentTestcase, inputFile, input, "", result, result.getStatus()));
                continue;
            }

            if (questionDTO.isStaticSolution()) {
                String expected = expectedOutputs.get(i);
                String status = outputsMatch(expected, result.getOutput()) ? "SUCCESS" : "WRONG_ANSWER";
                testcaseResults.add(buildTestcaseResult(currentTestcase, inputFile, input, expected, result, status));
                continue;
            }

            if (questionDTO.getChecker() == null
                    || questionDTO.getChecker().getCode() == null
                    || questionDTO.getChecker().getCode().isBlank()
                    || questionDTO.getChecker().getLanguage() == null
                    || questionDTO.getChecker().getLanguage().isBlank()) {
                result.setError("Custom checker is not configured");
                testcaseResults.add(buildTestcaseResult(currentTestcase, inputFile, input, "", result, "CHECKER_ERROR"));
                continue;
            }

            int resultIndex = testcaseResults.size();
            testcaseResults.add(null);
            pendingCheckerTestcases.add(new PendingCheckerTestcase(
                    resultIndex,
                    currentTestcase,
                    inputFile,
                    input,
                    result,
                    buildCheckerInput(input, result.getOutput())
            ));
        }

        if (pendingCheckerTestcases.isEmpty()) {
            return CompletableFuture.completedFuture(testcaseResults);
        }

        ExecutionJob validatorJob = new ExecutionJob();
        validatorJob.setJobId(jobId + "_validator");
        validatorJob.setSourceCode(questionDTO.getChecker().getCode());
        validatorJob.setLanguage(questionDTO.getChecker().getLanguage());

        List<String> checkerInputs = pendingCheckerTestcases.stream()
                .map(PendingCheckerTestcase::checkerInput)
                .toList();

        return containerPoolManager.executeTestcaseBatch(validatorJob, checkerInputs, this::shouldStopAfterCheckerResult)
                .thenApply(validatorResults -> {
                    for (int i = 0; i < pendingCheckerTestcases.size(); i++) {
                        PendingCheckerTestcase pending = pendingCheckerTestcases.get(i);
                        ExecutionResult validatorResult = i < validatorResults.size()
                                ? validatorResults.get(i)
                                : new ExecutionResult("", 0, "Missing checker result", "SYSTEM_ERROR");

                        if ("SKIPPED".equals(validatorResult.getStatus())) {
                            testcaseResults.set(pending.resultIndex(), buildTestcaseResult(
                                    pending.testcaseNumber(),
                                    pending.inputFile(),
                                    pending.input(),
                                    "",
                                    validatorResult,
                                    "SKIPPED"
                            ));
                            continue;
                        }

                        if (!"SUCCESS".equals(validatorResult.getStatus())) {
                            validatorResult.setError("Checker failed: " + validatorResult.getError());
                            testcaseResults.set(pending.resultIndex(), buildTestcaseResult(
                                    pending.testcaseNumber(),
                                    pending.inputFile(),
                                    pending.input(),
                                    "",
                                    validatorResult,
                                    "CHECKER_ERROR"
                            ));
                            continue;
                        }

                        String status = isCheckerAccepted(validatorResult.getOutput()) ? "SUCCESS" : "WRONG_ANSWER";
                        testcaseResults.set(pending.resultIndex(), buildTestcaseResult(
                                pending.testcaseNumber(),
                                pending.inputFile(),
                                pending.input(),
                                "",
                                pending.solutionResult(),
                                status
                        ));
                    }

                    return testcaseResults;
                });
    }

    private void publishSubmissionVerdict(List<TestcaseResult> testcaseResults, String jobId, ExecutionJob job) {
                    int passed = 0;
                    long totalTime = 0;
                    StringBuilder errorMsg = new StringBuilder();
                    String finalVerdict = "WRONG_ANSWER";

                    for (TestcaseResult res : testcaseResults) {
                            totalTime += res.getExecutionTimeMs();
                            String status = res.getStatus();
                            if ("SUCCESS".equalsIgnoreCase(status)) {
                                passed++;
                            } else if ("SKIPPED".equalsIgnoreCase(status)) {
                                continue;
                            } else {
                                finalVerdict = prioritizeFailureVerdict(finalVerdict, status);
                                errorMsg.append("Testcase ")
                                        .append(res.getTestcaseNumber())
                                        .append(": ")
                                        .append(status);
                                if (res.getError() != null && !res.getError().isBlank()) {
                                    errorMsg.append(" - ").append(res.getError());
                                }
                                errorMsg.append("\n");
                            }
                    }

                    SubmissionUpdate dto = new SubmissionUpdate();
                    dto.setTotalTestCases(testcaseResults.size());
                    dto.setPassedTestCases(passed);
                    dto.setExecutionTime(totalTime);
                    dto.setMemoryUsed(0L);
                    dto.setTestcaseResults(testcaseResults);

                    if (passed == testcaseResults.size()) {
                        dto.setVerdict("ACCEPTED");
                        redisService.markCompleted(jobId, "ACCEPTED", totalTime, job.getSourceCode(), job.getLanguage(), passed, testcaseResults.size(), "");
                    } else {
                        dto.setVerdict(finalVerdict);
                        dto.setErrorMessage(errorMsg.toString());
                        redisService.markError(jobId, finalVerdict, errorMsg.toString(), totalTime, job.getSourceCode(), job.getLanguage(), passed, testcaseResults.size());
                    }
                    submissionClient.updateSubmission(jobId, workerKey, dto);
    }

    private CompletableFuture<ExecutionResult> runSampleOrCustomInput(ExecutionJob job) {
        String jobId = job.getJobId();
        try {
            redisService.markRunning(jobId, job.getSourceCode(), job.getLanguage(), 0, 0);
            return containerPoolManager.executeSingleInput(job)
                    .thenApply(result -> {
                        if ("SUCCESS".equals(result.getStatus())) {
                            redisService.markCompleted(jobId, "SUCCESS", result.getExecutionTimeMs(), job.getSourceCode(), job.getLanguage(), 0, 0, result.getOutput());
                        } else {
                            redisService.markError(jobId, result.getStatus(), result.getError(), result.getExecutionTimeMs(), job.getSourceCode(), job.getLanguage(), 0, 0);
                        }
                        return result;
                    })
                    .exceptionally(ex -> {
                        redisService.markError(jobId, "SYSTEM_ERROR", ex.getMessage(), 0, job.getSourceCode(), job.getLanguage(), 0, 0);
                        return new ExecutionResult("SYSTEM_ERROR", 0, ex.getMessage(), "");
                    });
        } catch (Exception e) {
            redisService.markError(jobId, "ERROR", e.getMessage(), 0, job.getSourceCode(), job.getLanguage(), 0, 0);
            return CompletableFuture.completedFuture(new ExecutionResult("ERROR", 0, e.getMessage(), ""));
        }
    }

    private void failSubmission(String jobId, ExecutionJob job, String verdict, String message) {
        try {
            redisService.markError(jobId, verdict, message, 0, job.getSourceCode(), job.getLanguage(), 0, 0);
        } catch (Exception e) {
            log.warn("Failed to write execution error state for {}", jobId, e);
        }

        SubmissionUpdate dto = new SubmissionUpdate();
        dto.setVerdict(verdict);
        dto.setExecutionTime(0L);
        dto.setMemoryUsed(0L);
        dto.setTotalTestCases(0);
        dto.setPassedTestCases(0);
        dto.setErrorMessage(message);

        try {
            submissionClient.updateSubmission(jobId, workerKey, dto);
        } catch (Exception e) {
            log.warn("Failed to mark submission {} as {}", jobId, verdict, e);
        }
    }

    private void markJobFailure(ExecutionJob job, String verdict, String message) {
        if (job == null) {
            return;
        }

        if ("SUBMIT".equals(job.getTypeOfJob())) {
            failSubmission(job.getJobId(), job, verdict, message);
            return;
        }

        try {
            redisService.markError(job.getJobId(), verdict, message, 0, job.getSourceCode(), job.getLanguage(), 0, 0);
        } catch (Exception e) {
            log.warn("Failed to mark run job {} as {}", job.getJobId(), verdict, e);
        }
    }

    private String prioritizeFailureVerdict(String currentVerdict, String status) {
        if ("COMPILE_ERROR".equalsIgnoreCase(status)) {
            return "COMPILE_ERROR";
        }
        if ("TIME_LIMIT_EXCEEDED".equalsIgnoreCase(status) && !"COMPILE_ERROR".equals(currentVerdict)) {
            return "TIME_LIMIT_EXCEEDED";
        }
        if ("MEMORY_LIMIT_EXCEEDED".equalsIgnoreCase(status)
                && !"COMPILE_ERROR".equals(currentVerdict)
                && !"TIME_LIMIT_EXCEEDED".equals(currentVerdict)) {
            return "MEMORY_LIMIT_EXCEEDED";
        }
        if ("RUNTIME_ERROR".equalsIgnoreCase(status)
                && !"COMPILE_ERROR".equals(currentVerdict)
                && !"TIME_LIMIT_EXCEEDED".equals(currentVerdict)
                && !"MEMORY_LIMIT_EXCEEDED".equals(currentVerdict)) {
            return "RUNTIME_ERROR";
        }
        if ("CHECKER_ERROR".equalsIgnoreCase(status)
                && "WRONG_ANSWER".equals(currentVerdict)) {
            return "CHECKER_ERROR";
        }
        return currentVerdict;
    }

    private TestcaseResult buildTestcaseResult(
            int testcaseNumber,
            Path inputFile,
            String input,
            String expected,
            ExecutionResult result,
            String status
    ) {
        TestcaseResult testcaseResult = new TestcaseResult();
        testcaseResult.setTestcaseNumber(testcaseNumber);
        testcaseResult.setTestcaseName(inputFile.getFileName().toString());
        testcaseResult.setStatus(status);
        testcaseResult.setExecutionTimeMs(result.getExecutionTimeMs());
        testcaseResult.setInputPreview(preview(input));
        testcaseResult.setExpectedPreview(preview(expected));
        testcaseResult.setActualPreview(preview(result.getOutput()));
        testcaseResult.setError(preview(result.getError()));
        return testcaseResult;
    }

    private String preview(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = value.replace("\r\n", "\n").replace("\r", "\n").trim();
        return cleaned.length() <= 500 ? cleaned : cleaned.substring(0, 500) + "...";
    }

    private String buildCheckerInput(String input, String output) {
        String safeInput = input == null ? "" : input;
        String safeOutput = output == null ? "" : output;
        return safeInput + "\n" + safeOutput;
    }

    private boolean isCheckerAccepted(String output) {
        String checkerOutput = normalizeOutput(output).toUpperCase();
        return "1".equals(checkerOutput) || "PASS".equals(checkerOutput) || "TRUE".equals(checkerOutput);
    }

    private record PendingCheckerTestcase(
            int resultIndex,
            int testcaseNumber,
            Path inputFile,
            String input,
            ExecutionResult solutionResult,
            String checkerInput
    ) {
    }
}
