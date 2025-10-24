package com.example.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtTokenFilter implements GlobalFilter, Ordered {
  @Autowired private UserTokenRepository userTokenRepository;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private JwtTokenFilter self;

  @Value("${jwt.secretKey}")
  private String secretKey;

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

        if (isTokenBlacklisted(claims.getId())) {
          return Mono.error(
              new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token is blacklisted"));
        }
        String userId = claims.getSubject();
        String permission = (String) claims.get("permissions");
        String orgId = (String) claims.get("orgId");
        long tokenVersion = Long.parseLong(claims.get("tokenVersion").toString());
        UserToken userToken = self.getUserToken(Long.valueOf(userId));
        if (userToken != null && tokenVersion < userToken.getTokenVersion()) {
          throw new RuntimeException("Token version is outdated");
        }
        // Tạo request mới với header bổ sung
        ServerHttpRequest mutatedRequest =
            exchange
                .getRequest()
                .mutate()
                .header("user-id", userId)
                .header("org-id", orgId)
                .header("version", Long.toString(tokenVersion))
                .header("permisisons", permission)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());

      } catch (Exception e) {
        // Token sai/expired → cho đi tiếp hoặc reject tuỳ nhu cầu
        return chain.filter(exchange);
      }
    }

    return chain.filter(exchange);
  }

  @Cacheable(value = "userTokens", key = "#userId")
  public UserToken getUserToken(Long userId) {
    return userTokenRepository.findById(userId).orElse(null);
  }

  private boolean isTokenBlacklisted(String jti) {
    if (jti == null || jti.isEmpty()) {
      return false;
    }
    String key = "blacklist:accessTokens:" + jti;
    return Boolean.TRUE.equals(redisTemplate.hasKey(key));
  }

  @Override
  public int getOrder() {
    return -1;
  }
}
