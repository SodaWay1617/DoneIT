package com.doneit.mcp.security;

import com.doneit.user.domain.UserRepository;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(McpProperties.class)
public class McpSecurityConfiguration {
    @Bean McpTokenVerifier mcpTokens(McpProperties properties, Clock clock, com.doneit.mcp.infrastructure.McpTokenStore store) {
        return new McpTokenVerifier(properties, clock, store);
    }

    @Bean @Order(0)
    SecurityFilterChain mcpSecurity(HttpSecurity http, McpProperties properties, McpTokenVerifier tokens,
            UserRepository users, Clock clock) throws Exception {
        return http.securityMatcher("/mcp", "/mcp/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                // Async dispatch continues an already authenticated HTTP exchange (SSE).
                .authorizeHttpRequests(a -> a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll().anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((q,r,x) -> McpBearerFilter.reject(r,401))
                        .accessDeniedHandler((q,r,x) -> McpBearerFilter.reject(r,403)))
                .addFilterBefore(new McpBearerFilter(properties, tokens, users, clock), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
