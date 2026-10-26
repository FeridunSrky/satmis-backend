package com.satmis.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Not a @Component: Boot would otherwise also register it as a plain servlet filter
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_PATH_PREFIX = "/api/auth/";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    // Login/refresh must work even when the client still sends a stale access token
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(AUTH_PATH_PREFIX);
    }

    // A rejected token answers 401 even on public endpoints: silently treating the caller as anonymous
    // would hide viewer-specific data (seller phone, favorites) and the client would never refresh
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null
                && !authenticate(header.substring(BEARER_PREFIX.length()), request)) {
            authenticationEntryPoint.commence(request, response,
                    new InsufficientAuthenticationException("Bearer token rejected"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean authenticate(String token, HttpServletRequest request) {
        try {
            CustomUserDetails userDetails = userDetailsService.loadUserByUsername(jwtService.extractUsername(token));
            if (userDetails.isEnabled() && jwtService.isValid(token, userDetails)) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                return true;
            }
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            log.debug("Bearer token rejected: {}", ex.getClass().getSimpleName());
        }
        return false;
    }
}
