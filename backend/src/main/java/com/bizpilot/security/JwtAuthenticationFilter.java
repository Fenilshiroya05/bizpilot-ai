package com.bizpilot.security;

import com.bizpilot.security.jwt.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validates the {@code Authorization: Bearer <token>} header on every request
 * and, if valid, populates the {@link SecurityContextHolder} with a
 * {@link UserPrincipal} built entirely from the token's claims (no database
 * lookup). Invalid/expired/malformed/missing tokens simply leave the request
 * unauthenticated — Spring Security's exception handling (see
 * {@code RestAuthenticationEntryPoint}) rejects it downstream if the endpoint
 * requires authentication.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        extractToken(request)
                .flatMap(jwtService::parseAndValidate)
                .ifPresent(this::authenticate);
        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private void authenticate(Jws<Claims> claims) {
        try {
            UUID userId = jwtService.extractUserId(claims);
            UUID organizationId = jwtService.extractOrganizationId(claims);
            Set<String> authorityNames = jwtService.extractAuthorities(claims);
            UserPrincipal principal = new UserPrincipal(userId, organizationId, authorityNames);

            Set<GrantedAuthority> authorities = authorityNames.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toUnmodifiableSet());

            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException e) {
            // A validly-signed token with a missing/malformed claim (e.g. one minted
            // before a claim existed) must fail exactly like any other invalid token —
            // left unauthenticated, not an uncaught exception escaping the filter chain
            // (which would bypass RestAuthenticationEntryPoint's ApiError envelope).
        }
    }
}
