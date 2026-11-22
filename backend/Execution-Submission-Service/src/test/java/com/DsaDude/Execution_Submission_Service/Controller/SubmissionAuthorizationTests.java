package com.DsaDude.Execution_Submission_Service.Controller;

import com.DsaDude.Execution_Submission_Service.DTO.ExecutionJob;
import com.DsaDude.Execution_Submission_Service.DTO.SubmissionUpdateDTO;
import com.DsaDude.Execution_Submission_Service.Model.Submission;
import com.DsaDude.Execution_Submission_Service.Model.TestcaseResult;
import com.DsaDude.Execution_Submission_Service.Repository.SubmissionRepository;
import com.DsaDude.Execution_Submission_Service.Service.ExecutionJobService;
import com.DsaDude.Execution_Submission_Service.Service.SubmissionAccess;
import com.DsaDude.Execution_Submission_Service.Service.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SubmissionAuthorizationTests {
    private SubmissionRepository repository;
    private SubmissionService submissions;
    private ExecutionJobService executionJobs;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = mock(SubmissionRepository.class);
        submissions = spy(new SubmissionService(repository, null, null));
        executionJobs = mock(ExecutionJobService.class);
        SubmissionAccess access = new SubmissionAccess("gateway-test-key", "worker-test-key");
        mvc = MockMvcBuilders.standaloneSetup(new SubmissionController(submissions, access),
                new InternalSubmissionController(submissions, access), new JobController(executionJobs, access, submissions))
                .build();
    }

    @Test
    void ownerCanReadCodeWithoutReceivingHiddenFixtures() throws Exception {
        Submission submission = new Submission();
        submission.setId("submission-1");
        submission.setUserId(7);
        submission.setSourceCode("print(42)");
        TestcaseResult hidden = new TestcaseResult();
        hidden.setInputPreview("hidden fixture");
        submission.setTestcaseResults(List.of(hidden));
        when(repository.findByIdAndUserId("submission-1", 7)).thenReturn(Optional.of(submission));
        when(repository.findByUserId(7)).thenReturn(List.of(submission));

        mvc.perform(get("/api/submissions/submission-1")
                        .header("X-USER-ID", 7).header("X-DSACHAMP-GATEWAY-KEY", "gateway-test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("submission-1"))
                .andExpect(jsonPath("$.sourceCode").value("print(42)"))
                .andExpect(jsonPath("$.testcaseResults").doesNotExist());

        mvc.perform(get("/api/submissions/all-submissions/7")
                        .header("X-USER-ID", 7).header("X-DSACHAMP-GATEWAY-KEY", "gateway-test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceCode").doesNotExist());
    }

    @Test
    void anotherUserCannotReadDetailsHistoryOrPollingData() throws Exception {
        mvc.perform(get("/api/submissions/all-submissions/7")
                        .header("X-USER-ID", 8).header("X-DSACHAMP-GATEWAY-KEY", "gateway-test-key"))
                .andExpect(status().isForbidden());

        for (String path : List.of("/api/submissions/submission-1", "/api/code/submission-1",
                "/api/code/submit/submission-1")) {
            mvc.perform(get(path).header("X-USER-ID", 8).header("X-DSACHAMP-GATEWAY-KEY", "gateway-test-key"))
                    .andExpect(status().isNotFound());
        }
        verifyNoInteractions(executionJobs);
        verify(repository, never()).findByUserId(7);
    }

    @Test
    void identityHeaderAloneDoesNotGrantAccess() throws Exception {
        mvc.perform(get("/api/submissions/all-submissions/7").header("X-USER-ID", 7))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test
    void routeAndVerifiedIdentityDetermineJobTypeAndOwner() throws Exception {
        when(executionJobs.enqueueExecution(any())).thenReturn(ResponseEntity.ok().build());
        String body = "{\"userId\":42,\"typeOfJob\":\"RUN\"}";
        mvc.perform(post("/api/code/submit").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-USER-ID", 7).header("X-DSACHAMP-GATEWAY-KEY", "gateway-test-key"))
                .andExpect(status().isOk());
        ArgumentCaptor<ExecutionJob> job = ArgumentCaptor.forClass(ExecutionJob.class);
        verify(executionJobs).enqueueExecution(job.capture());
        assertEquals(7, job.getValue().getUserId());
        assertEquals("SUBMIT", job.getValue().getTypeOfJob());

        mvc.perform(post("/api/code/run").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42,\"typeOfJob\":\"SUBMIT\"}"))
                .andExpect(status().isOk());
        verify(executionJobs).enqueueJob(job.capture());
        assertEquals(0, job.getValue().getUserId());
        assertEquals("RUN", job.getValue().getTypeOfJob());
    }

    @Test
    void anonymousRunPollingCannotReadStoredSubmission() throws Exception {
        String id = "12345678-abcd-abcd-abcd-123456789abc";
        when(repository.existsById(id)).thenReturn(true);
        mvc.perform(get("/api/code/run/" + id)).andExpect(status().isNotFound());
        verifyNoInteractions(executionJobs);
    }

    @Test
    void onlyWorkerCredentialCanWriteVerdict() throws Exception {
        String path = "/internal/submissions/submission-1/verdict";
        String body = "{\"verdict\":\"ACCEPTED\"}";
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-DSACHAMP-GATEWAY-KEY", "gateway-test-key"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/submissions/update/submission-1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        verify(submissions, never()).updateSubmission(any(), any());

        doReturn(null).when(submissions).updateSubmission(eq("submission-1"), any(SubmissionUpdateDTO.class));
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-DSACHAMP-WORKER-KEY", "worker-test-key"))
                .andExpect(status().isNoContent());
        verify(submissions).updateSubmission(eq("submission-1"), any(SubmissionUpdateDTO.class));
    }
}
