package com.gymora.security.tenant;

import com.gymora.common.tenant.TenantContext;
import com.gymora.security.GymoraRoles;
import com.gymora.security.jwt.TokenClaims;
import com.gymora.security.tenant.TenantStatusProvider.TenantStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Puts the gym from the token's {@code tenant_id} claim into {@link TenantContext} for the length
 * of the request.
 *
 * <ul>
 *   <li>Platform admins never get a tenant, even if their token carries one.</li>
 *   <li>Every other authenticated user must carry a valid tenant, or gets 403.</li>
 *   <li>The gym must exist and be active, or every request gets 403.</li>
 * </ul>
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private final MessageSource messages;
    private final LocaleResolver localeResolver;
    private final ObjectProvider<TenantStatusProvider> statusProvider;

    public TenantContextFilter(MessageSource messages, LocaleResolver localeResolver,
                               ObjectProvider<TenantStatusProvider> statusProvider) {
        this.messages = messages;
        this.localeResolver = localeResolver;
        this.statusProvider = statusProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            chain.doFilter(request, response);
            return;
        }

        boolean platformAdmin = TokenClaims.roles(token.getToken()).contains(GymoraRoles.PLATFORM_ADMIN);
        if (platformAdmin) {
            chain.doFilter(request, response);
            return;
        }

        Optional<UUID> tenantId = TokenClaims.tenantId(token.getToken());
        if (tenantId.isEmpty()) {
            writeForbidden(request, response, "error.tenant_required");
            return;
        }

        TenantStatusProvider provider = statusProvider.getIfAvailable();
        TenantStatus status = provider == null ? TenantStatus.ACTIVE : provider.statusOf(tenantId.get());
        if (status == TenantStatus.SUSPENDED) {
            writeForbidden(request, response, "error.tenant_suspended");
            return;
        }
        if (status == TenantStatus.UNKNOWN) {
            writeForbidden(request, response, "error.tenant_unknown");
            return;
        }

        try {
            TenantContext.set(tenantId.get());
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void writeForbidden(HttpServletRequest request, HttpServletResponse response, String code)
            throws IOException {
        Locale locale = localeResolver.resolveLocale(request);
        String detail = messages.getMessage(code, null, code, locale);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\""
                + escape(detail) + "\",\"code\":\"" + code + "\"}");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
