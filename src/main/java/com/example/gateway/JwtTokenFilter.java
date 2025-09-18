package com.example.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtTokenFilter implements GlobalFilter, Ordered {

  private final String secretKey = "theanh-2003-3002";

  private static final List<String> WHITELIST =
      List.of("/auth/login", "/auth/register", "/auth/verify-otp");

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();

    // Bỏ qua nếu thuộc whitelist
    if (WHITELIST.stream().anyMatch(path::startsWith)) {
      return chain.filter(exchange);
    }

    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    System.out.println("------------------------------------------------------");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);

      try {
        Claims claims =
            Jwts.parserBuilder()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .build()
                .parseClaimsJws(token)
                .getBody();

        String userId = claims.getSubject();
        String permission = (String) claims.get("permissions");
        String orgId = (String) claims.get("orgId");

        // Tạo request mới với header bổ sung
        ServerHttpRequest mutatedRequest =
            exchange
                .getRequest()
                .mutate()
                .header("x-user-id", userId)
                .header("x-org-id", orgId)
                .header("x-org-permisisons", permission)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());

      } catch (Exception e) {
        // Token sai/expired → cho đi tiếp hoặc reject tuỳ nhu cầu
        return chain.filter(exchange);
      }
    }

    return chain.filter(exchange);
  }

  @Override
  public int getOrder() {
    return -1;
  }
}
