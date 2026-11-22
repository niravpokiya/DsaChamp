package com.DsaDude.api_gateway.Auth;

import com.DsaDude.api_gateway.Service.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtFilterTests {
    private final JwtFilter filter = new JwtFilter();
    private final JwtUtil jwt = mock(JwtUtil.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "jwtUtil", jwt);
        ReflectionTestUtils.setField(filter, "tokenBlacklistService", mock(TokenBlacklistService.class));
        ReflectionTestUtils.setField(filter, "gatewayKey", "gateway-test-key");
    }

    @Test
    void authenticatedRequestReplacesClientIdentityAndDropsWorkerCredential() {
        when(jwt.validateToken("token")).thenReturn(true);
        when(jwt.extractUsername("token")).thenReturn("alice");
        when(jwt.extractRole("token")).thenReturn("USER");
        when(jwt.extractUserId("token")).thenReturn(7L);
        MockServerWebExchange request = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/submissions/submission-1")
                .header("Authorization", "Bearer token")
                .header("X-USER-ID", "42", "43")
                .header("X-ROLE", "ADMIN")
                .header("X-DSACHAMP-GATEWAY-KEY", "client-key")
                .header("X-DSACHAMP-WORKER-KEY", "client-key")
                .build());

        filter.filter(request, forwarded -> {
            var headers = forwarded.getRequest().getHeaders();
            assertEquals(List.of("7"), headers.get("X-USER-ID"));
            assertEquals(List.of("USER"), headers.get("X-ROLE"));
            assertEquals(List.of("gateway-test-key"), headers.get("X-DSACHAMP-GATEWAY-KEY"));
            assertFalse(headers.containsKey("X-DSACHAMP-WORKER-KEY"));
            return forwarded.getResponse().setComplete();
        }).block();
    }

    @Test
    void sampleRunPrefixDoesNotMakeOtherRoutesPublic() {
        MockServerWebExchange request = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/code/run-other"));
        filter.filter(request, forwarded -> fail("Unauthenticated request was forwarded")).block();
        assertEquals(HttpStatus.UNAUTHORIZED, request.getResponse().getStatusCode());
    }
}
