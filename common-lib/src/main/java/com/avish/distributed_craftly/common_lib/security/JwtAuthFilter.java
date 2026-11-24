package com.avish.distributed_craftly.common_lib.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final AuthUtil authUtil;
    private final HandlerExceptionResolver handlerExceptionResolver;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        try {
            log.info("Incoming request: {}", request.getRequestURI());

            final String requestHeaderToken = request.getHeader("Authorization");
            if (requestHeaderToken == null || !requestHeaderToken.startsWith("Bearer")) {
                filterChain.doFilter(request, response);
                return;
            }
            String jwtToken = requestHeaderToken.split(" ")[1];
            JwtUserPrincipal user = authUtil.verifyAccessToken(jwtToken);
            if (user != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        user, jwtToken, user.authorities()
                );
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
            filterChain.doFilter(request, response);
        }
        catch (Exception ex) {
            handlerExceptionResolver.resolveException(request, response, null, ex);
        }
    }

    // Streaming endpoints (SSE) finish on an ASYNC dispatch, which runs the security
    // filter chain a second time on a fresh thread with an empty SecurityContext.
    // Without this the AuthorizationFilter denies that dispatch after the response
    // has already been committed.
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}
