package com.DsaDude.api_gateway.Auth;

import com.DsaDude.api_gateway.Service.TokenBlacklistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class JwtFilter implements GlobalFilter {

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private TokenBlacklistService tokenBlacklistService;
    @Value("${DSACHAMP_GATEWAY_KEY:}")
    private String gatewayKey;

    private static final Map<String, List<String>> ROLE_ACCESS = Map.of(
            "/api/admin/", List.of("ADMIN"),
            "/api/question/", List.of("USER", "ADMIN"),
            "/api/submissions/", List.of("USER", "ADMIN")
    );

    private static final List<String> PUBLIC_ROUTES = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/question/all-published"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();
        ServerHttpRequest sanitized = exchange.getRequest().mutate().headers(headers -> {
            headers.remove("X-USER");
            headers.remove("X-ROLE");
            headers.remove("X-USER-ID");
            headers.remove("X-DSACHAMP-GATEWAY-KEY");
            headers.remove("X-DSACHAMP-WORKER-KEY");
            headers.remove("X-DSACHAMP-CONTEST-KEY");
        }).build();
        exchange = exchange.mutate().request(sanitized).build();

        // Allow preflight CORS requests
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);
        }

        // PUBLIC ROUTES
        boolean isPublic = PUBLIC_ROUTES.contains(path)
                || (HttpMethod.POST.equals(method) && path.equals("/api/code/run"))
                || (HttpMethod.GET.equals(method)
                    && path.matches("/api/code/run/[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"));

        if (isPublic) {
            return chain.filter(exchange);
        }

        // JWT CHECK
        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst("Authorization");

        if (authHeader == null ||
                !authHeader.startsWith("Bearer ")) {
            exchange.getResponse()
                    .setStatusCode(HttpStatus.UNAUTHORIZED);

            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        boolean valid = jwtUtil.validateToken(token);

        if (!valid) {
            exchange.getResponse()
                    .setStatusCode(HttpStatus.UNAUTHORIZED);

            return exchange.getResponse().setComplete();
        }

        /* check for revoked token */
        if(tokenBlacklistService.isBlacklisted(token)){
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String username = jwtUtil.extractUsername(token);

        String role = jwtUtil.extractRole(token);

        // ROLE based AUTHORIZATION
        String matchedPath = ROLE_ACCESS.keySet()
                .stream()
                .filter(path::startsWith)
                .max(Comparator.comparingInt(
                        String::length
                ))
                .orElse(null);

        if (matchedPath != null) {
            List<String> allowedRoles = ROLE_ACCESS.get(matchedPath);
            if (!allowedRoles.contains(role)) {
                exchange.getResponse()
                        .setStatusCode(
                                HttpStatus.FORBIDDEN
                        );
                return exchange
                        .getResponse()
                        .setComplete();
            }
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                        .header("X-USER", username)
                        .header("X-ROLE", role)
                        .header("X-USER-ID",
                                String.valueOf(
                                        jwtUtil.extractUserId(token)
                                ))
                        .headers(headers -> {
                            if (!gatewayKey.isBlank() && (path.startsWith("/api/submissions/")
                                    || path.startsWith("/api/code/")
                                    || path.equals("/api/contests")
                                    || path.startsWith("/api/contests/")
                                    || path.matches("/api/question/[^/]+/submissions"))) {
                                headers.set("X-DSACHAMP-GATEWAY-KEY", gatewayKey);
                            }
                        })
                        .build();

        return chain.filter(
                exchange.mutate()
                        .request(request)
                        .build()
        );
    }
}
