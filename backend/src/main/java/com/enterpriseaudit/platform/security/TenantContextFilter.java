package com.enterpriseaudit.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.io.UncheckedIOException;

@Component
public class TenantContextFilter extends OncePerRequestFilter {
    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbc;

    public TenantContextFilter(TransactionTemplate transactions, JdbcTemplate jdbc) {
        this.transactions = transactions;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Object claim = jwtAuth.getToken().getClaims().get("tenant_id");
            if (claim == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "JWT must contain tenant_id");
                return;
            }
            try {
                TenantContext.set(UUID.fromString(claim.toString()));
            } catch (IllegalArgumentException invalidTenant) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "JWT tenant_id must be a UUID");
                return;
            }
        }
        try {
            if (authentication instanceof JwtAuthenticationToken) {
                transactions.executeWithoutResult(status -> {
                    jdbc.queryForObject("select set_config('app.current_tenant', ?, true)", String.class,
                            TenantContext.requireTenantId().toString());
                    try {
                        chain.doFilter(request, response);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    } catch (ServletException e) {
                        throw new IllegalStateException(e);
                    }
                });
            } else {
                chain.doFilter(request, response);
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } finally {
            TenantContext.clear();
        }
    }
}
