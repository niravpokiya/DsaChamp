package com.DsaDude.Execution_worker_Service.Service;

import com.DsaDude.Execution_worker_Service.DTO.ExecutionJob;
import com.DsaDude.Execution_worker_Service.DTO.ExecutionResult;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

@Component
public class ContainerPoolManager {

    private static final int POOL_SIZE = 8;
    private static final int DOCKER_EXEC_TIMEOUT_SECONDS = 20;
    private static final List<String> SUPPORTED_LANGUAGES = List.of("cpp", "java", "python");
    private static final Map<String, String> RUNNER_IMAGES = Map.of(
            "cpp", "cpp-runner",
            "java", "java-runner",
            "python", "python-runner"
    );

    private final Map<String, BlockingQueue<String>> availableContainersByLanguage = new HashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE * SUPPORTED_LANGUAGES.size());

    @PostConstruct
    public void initializeRunnerPool() throws Exception {
        for (String language : SUPPORTED_LANGUAGES) {
            String imageName = RUNNER_IMAGES.get(language);
            BlockingQueue<String> availableContainers = new LinkedBlockingQueue<>();
            availableContainersByLanguage.put(language, availableContainers);

            for (int i = 1; i <= POOL_SIZE; i++) {
                new ProcessBuilder("docker", "rm", "-f", imageName + "-" + i)
                        .start()
                        .waitFor();
            }

            for (int i = 1; i <= POOL_SIZE; i++) {
                String containerName = imageName + "-" + i;
                startRunnerContainer(containerName, imageName);
                availableContainers.add(containerName);
            }
        }
    }

    private void startRunnerContainer(String containerName, String imageName) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "run", "-dit",
                "--name", containerName,
                "--network", "none",
                "--memory", "256m",
                "--cpus", "1",
                "--pids-limit", "128",
                "--security-opt", "no-new-privileges",
                "--cap-drop", "ALL",
                imageName, "/bin/bash"
        );

        processBuilder.redirectErrorStream(true);
        Process p = processBuilder.start();

        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.out.println("Docker Output: " + line);
            }
        }

        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("Failed to start container: " + containerName + " (Exit code " + exit + ")");
        }
    }

    public CompletableFuture<ExecutionResult> executeSingleInput(ExecutionJob job) {
        return CompletableFuture.supplyAsync(() -> {
            String language = normalizeLanguage(job.getLanguage());
            if (!isSupported(language)) {
                return unsupportedLanguage(job.getLanguage());
            }

            String containerName = null;

            try {
                containerName = availableContainersByLanguage.get(language).take();
                return runSingleInputInContainer(containerName, job);
            } catch (Exception e) {
                return new ExecutionResult("", 0, e.getMessage(), "SYSTEM_ERROR");
            } finally {
                releaseContainer(language, containerName);
            }
        }, executor);
    }

    public CompletableFuture<List<ExecutionResult>> executeTestcaseBatch(ExecutionJob job, List<String> inputs) {
        return executeTestcaseBatch(job, inputs, null);
    }

    public CompletableFuture<List<ExecutionResult>> executeTestcaseBatch(
            ExecutionJob job,
            List<String> inputs,
            BiPredicate<Integer, ExecutionResult> shouldStopAfterResult) {
        return CompletableFuture.supplyAsync(() -> {
            String language = normalizeLanguage(job.getLanguage());
            if (!isSupported(language)) {
                return repeatedResult(inputs.size(), unsupportedLanguage(job.getLanguage()));
            }

            String containerName = null;

            try {
                containerName = availableContainersByLanguage.get(language).take();
                return runTestcaseBatchInContainer(containerName, job, inputs, shouldStopAfterResult);
            } catch (Exception e) {
                List<ExecutionResult> results = new ArrayList<>();
                for (int i = 0; i < inputs.size(); i++) {
                    results.add(new ExecutionResult("", 0, e.getMessage(), "SYSTEM_ERROR"));
                }
                return results;
            } finally {
                releaseContainer(language, containerName);
            }
        }, executor);
    }

    private void releaseContainer(String language, String containerName) {
        if (language != null && containerName != null) {
            availableContainersByLanguage.get(language).add(containerName);
        }
    }

    private boolean isSupported(String language) {
        return language != null && RUNNER_IMAGES.containsKey(language);
    }

    private ExecutionResult unsupportedLanguage(String language) {
        return new ExecutionResult("", 0, "Unsupported language: " + language, "UNSUPPORTED_LANGUAGE");
    }

    private List<ExecutionResult> runTestcaseBatchInContainer(
            String containerName,
            ExecutionJob job,
            List<String> inputs,
            BiPredicate<Integer, ExecutionResult> shouldStopAfterResult) throws Exception {

        String language = normalizeLanguage(job.getLanguage());
        Path tempDir = Files.createTempDirectory("exec-submit-");
        String sourceFileName = getSourceFileName(language);
        Path sourceFile = tempDir.resolve(sourceFileName);
        Path inputFile = tempDir.resolve("input.txt");

        try {
            Files.writeString(sourceFile, job.getSourceCode());

            runDockerCommand(new ProcessBuilder("docker", "cp",
                    sourceFile.toString(),
                    containerName + ":/workspace/" + sourceFileName));

            String compileCommand = buildCompileCommand(language, sourceFileName);
            if (compileCommand != null) {
                ExecutionResult compileResult = runDockerExecCommand(containerName, compileCommand);
                if (!"SUCCESS".equals(compileResult.getStatus())) {
                    return resultWithSkippedRemainder(inputs.size(), compileResult);
                }
            }

            List<ExecutionResult> results = new ArrayList<>();
            String runCommand = buildRunCommand(language, sourceFileName);

            for (int i = 0; i < inputs.size(); i++) {
                String input = inputs.get(i);
                Files.writeString(inputFile, input == null ? "" : input);
                runDockerCommand(new ProcessBuilder("docker", "cp",
                        inputFile.toString(),
                        containerName + ":/workspace/input.txt"));
                ExecutionResult result = runDockerExecCommand(containerName, runCommand);
                results.add(result);

                if (shouldStopBatch(result) || shouldStopAfterResult(shouldStopAfterResult, i, result)) {
                    addSkippedResults(results, inputs.size() - i - 1, result.getStatus());
                    break;
                }
            }

            return results;
        } finally {
            cleanupWorkspace(containerName);

            try {
                Files.deleteIfExists(sourceFile);
                Files.deleteIfExists(inputFile);
                Files.deleteIfExists(tempDir);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean shouldStopAfterResult(
            BiPredicate<Integer, ExecutionResult> shouldStopAfterResult,
            int inputIndex,
            ExecutionResult result) {
        return shouldStopAfterResult != null && shouldStopAfterResult.test(inputIndex, result);
    }

    private List<ExecutionResult> resultWithSkippedRemainder(int totalCount, ExecutionResult firstResult) {
        List<ExecutionResult> results = new ArrayList<>();
        if (totalCount <= 0) {
            return results;
        }

        results.add(firstResult);
        addSkippedResults(results, totalCount - 1, firstResult.getStatus());
        return results;
    }

    private void addSkippedResults(List<ExecutionResult> results, int count, String reasonStatus) {
        for (int i = 0; i < count; i++) {
            results.add(new ExecutionResult(
                    "",
                    0,
                    "Skipped after " + reasonStatus + " on an earlier testcase",
                    "SKIPPED"));
        }
    }

    private boolean shouldStopBatch(ExecutionResult result) {
        if (result == null || result.getStatus() == null) {
            return false;
        }

        return switch (result.getStatus()) {
            case "COMPILE_ERROR", "RUNTIME_ERROR", "TIME_LIMIT_EXCEEDED", "MEMORY_LIMIT_EXCEEDED", "SYSTEM_ERROR", "ERROR" -> true;
            default -> false;
        };
    }

    private List<ExecutionResult> repeatedResult(int count, ExecutionResult result) {
        List<ExecutionResult> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(new ExecutionResult(
                    result.getOutput(),
                    i == 0 ? result.getExecutionTimeMs() : 0,
                    result.getError(),
                    result.getStatus()));
        }
        return results;
    }

    private ExecutionResult runSingleInputInContainer(String containerName, ExecutionJob job) throws Exception {
        String language = normalizeLanguage(job.getLanguage());
        Path tempDir = Files.createTempDirectory("exec-");
        String sourceFileName = getSourceFileName(language);
        Path sourceFile = tempDir.resolve(sourceFileName);
        Path inputFile = tempDir.resolve("input.txt");

        try {
            Files.writeString(sourceFile, job.getSourceCode());
            Files.writeString(inputFile, job.getInput() == null ? "" : job.getInput());

            long start = System.currentTimeMillis();

            runDockerCommand(new ProcessBuilder("docker", "cp",
                    sourceFile.toString(),
                    containerName + ":/workspace/" + sourceFileName));

            runDockerCommand(new ProcessBuilder("docker", "cp",
                    inputFile.toString(),
                    containerName + ":/workspace/input.txt"));

            String execCommand = buildExecutionCommand(language, sourceFileName);
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "docker", "exec",
                    containerName,
                    "bash", "-c",
                    execCommand
            );
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            CompletableFuture<String> outputFuture =
                    CompletableFuture.supplyAsync(() -> readLimitedOutputQuietly(process));

            boolean finished = process.waitFor(DOCKER_EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long durationMs = System.currentTimeMillis() - start;

            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult("", durationMs, "Execution worker timed out", "SYSTEM_ERROR");
            }

            int exitCode = process.exitValue();
            String output = outputFuture.get(2, TimeUnit.SECONDS);

            return mapProcessExitToResult(exitCode, output, durationMs);
        } finally {
            cleanupWorkspace(containerName);

            try {
                Files.deleteIfExists(sourceFile);
                Files.deleteIfExists(inputFile);
                Files.deleteIfExists(tempDir);
            } catch (Exception ignored) {
            }
        }
    }

    private String getSourceFileName(String language) {
        return switch (language) {
            case "cpp" -> "main.cpp";
            case "java" -> "Main.java";
            case "python" -> "solution.py";
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private String buildExecutionCommand(String language, String sourceFileName) {
        return switch (language) {
            case "cpp", "java" -> buildCompileCommand(language, sourceFileName) + " && " + buildRunCommand(language, sourceFileName);
            case "python" -> buildRunCommand(language, sourceFileName);
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private String buildCompileCommand(String language, String sourceFileName) {
        return switch (language) {
            case "cpp" -> "cd /workspace && " +
                    "timeout 10s g++ " + sourceFileName + " -o main 2> compile_error.txt; " +
                    "COMPILE_EXIT=$?; " +
                    "if [ $COMPILE_EXIT -eq 124 ]; then echo 'Compilation timed out'; exit 1; fi; " +
                    "if [ $COMPILE_EXIT -ne 0 ] || [ -s compile_error.txt ]; then cat compile_error.txt; exit 1; fi";
            case "java" -> "cd /workspace && " +
                    "timeout 10s javac " + sourceFileName + " 2> compile_error.txt; " +
                    "COMPILE_EXIT=$?; " +
                    "if [ $COMPILE_EXIT -eq 124 ]; then echo 'Compilation timed out'; exit 1; fi; " +
                    "if [ $COMPILE_EXIT -ne 0 ] || [ -s compile_error.txt ]; then cat compile_error.txt; exit 1; fi";
            case "python" -> null;
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private String buildRunCommand(String language, String sourceFileName) {
        String interpreterCommand = switch (language) {
            case "cpp" -> "./main";
            case "java" -> "java Main";
            case "python" -> "python3 " + sourceFileName;
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };

        return "cd /workspace && " +
                "timeout 2s " + interpreterCommand + " < input.txt > output.txt 2> runtime_error.txt; " +
                "EXIT_CODE=$?; " +
                "if [ $EXIT_CODE -eq 124 ]; then exit 124; fi; " +
                "if [ -s runtime_error.txt ]; then cat runtime_error.txt; rm -f runtime_error.txt output.txt; exit 2; fi; " +
                "cat output.txt; rm -f runtime_error.txt output.txt";
    }

    private ExecutionResult runDockerExecCommand(String containerName, String command) throws Exception {
        long start = System.currentTimeMillis();
        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "exec",
                containerName,
                "bash", "-c",
                command
        );
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        CompletableFuture<String> outputFuture =
                CompletableFuture.supplyAsync(() -> readLimitedOutputQuietly(process));

        boolean finished = process.waitFor(DOCKER_EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        long durationMs = System.currentTimeMillis() - start;

        if (!finished) {
            process.destroyForcibly();
            return new ExecutionResult("", durationMs, "Execution worker timed out", "SYSTEM_ERROR");
        }

        int exitCode = process.exitValue();
        String output = outputFuture.get(2, TimeUnit.SECONDS);
        return mapProcessExitToResult(exitCode, output, durationMs);
    }

    private ExecutionResult mapProcessExitToResult(int exitCode, String output, long durationMs) {
        return switch (exitCode) {
            case 0 -> new ExecutionResult(output, durationMs, "", "SUCCESS");
            case 1 -> new ExecutionResult("", durationMs, output, "COMPILE_ERROR");
            case 2 -> new ExecutionResult("", durationMs, output, "RUNTIME_ERROR");
            case 124 -> new ExecutionResult("", durationMs, "Time Limit Exceeded", "TIME_LIMIT_EXCEEDED");
            case 137 -> new ExecutionResult("", durationMs, "Memory Limit Exceeded", "MEMORY_LIMIT_EXCEEDED");
            default -> new ExecutionResult("", durationMs, "Unknown Error: " + output, "ERROR");
        };
    }

    private String normalizeLanguage(String language) {
        if (language == null) {
            return null;
        }

        return switch (language.toLowerCase()) {
            case "cpp", "c++" -> "cpp";
            case "java" -> "java";
            case "python", "py" -> "python";
            default -> null;
        };
    }

    private void runDockerCommand(ProcessBuilder processBuilder) throws Exception {
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        CompletableFuture<String> outputFuture =
                CompletableFuture.supplyAsync(() -> readLimitedOutputQuietly(process));
        boolean finished = process.waitFor(DOCKER_EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Docker command timed out");
        }
        String output = outputFuture.get(2, TimeUnit.SECONDS);
        if (process.exitValue() != 0) {
            throw new RuntimeException("Docker command failed: " + output);
        }
    }

    private void cleanupWorkspace(String containerName) {
        try {
            new ProcessBuilder("docker", "exec",
                    containerName,
                    "bash", "-c",
                    "cd /workspace && rm -f main.cpp Main.java solution.py main Main.class input.txt output.txt compile_error.txt runtime_error.txt")
                    .start()
                    .waitFor();
        } catch (Exception ignored) {
        }
    }

    private String readLimitedOutputQuietly(Process process) {
        try {
            return readLimitedOutput(process);
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    private String readLimitedOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (Reader reader = new InputStreamReader(process.getInputStream())) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                output.append(buffer, 0, read);
            }
        }
        return output.toString();
    }
}
