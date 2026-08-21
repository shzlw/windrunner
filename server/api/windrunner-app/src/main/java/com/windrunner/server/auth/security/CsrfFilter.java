package com.windrunner.server.auth.security;

import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.domain.AuthSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CsrfFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of(
            HttpMethod.GET.name(),
            HttpMethod.HEAD.name(),
            HttpMethod.OPTIONS.name(),
            HttpMethod.TRACE.name()
    );

    private final AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthSession authSession = authService.resolveAuthSession(request).orElse(null);
        if (authSession == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String csrfHeader = request.getHeader("X-CSRF-Token");
        if (!StringUtils.hasText(csrfHeader) || !csrfHeader.equals(authSession.getCsrfToken())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid CSRF token");
        }

        filterChain.doFilter(request, response);
    }
}
