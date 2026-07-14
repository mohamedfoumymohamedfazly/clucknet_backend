package com.clucknet.backend.security.jwt;

import com.clucknet.backend.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, CustomUserDetailsService userDetailsService) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth") || 
               path.startsWith("/v3/api-docs") || 
               path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        String authHeader = request.getHeader("Authorization");
        
        log.debug("Incoming request URL: {}", requestURI);
        log.debug("Authorization header presence: {}", StringUtils.hasText(authHeader));
        
        // Before validating token logic
        if (requestURI.startsWith("/api/auth") ||
            requestURI.startsWith("/v3/api-docs") ||
            requestURI.startsWith("/swagger-ui")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                if (tokenProvider.validateToken(jwt)) {
                    String username = tokenProvider.getUsernameFromJwt(jwt);
                    log.debug("JWT validation success for user: {}. Path: {}", username, requestURI);

                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.warn("JWT validation failed for request to: {}", requestURI);
                    sendUnauthorizedResponse(response, "Invalid or expired JWT token");
                    return;
                }
            } else {
                log.debug("No JWT token found in request headers for URL: {}", requestURI);
            }
        } catch (Exception ex) {
            log.error("Failed to set user authentication in security context for path {}: {}", requestURI, ex.getMessage());
            sendUnauthorizedResponse(response, "Unauthorized: " + ex.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    // Parse Authorization bearer header
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken == null || bearerToken.trim().isEmpty()) {
            return null;
        }
        
        String trimmedToken = bearerToken.trim();
        if (trimmedToken.equalsIgnoreCase("bearer null") || trimmedToken.equalsIgnoreCase("null")) {
            return null;
        }
        
        String token = trimmedToken;
        // Keep stripping "Bearer" or "Bearer " in case of double prefixing
        while (token.toLowerCase().startsWith("bearer")) {
            token = token.substring(6).trim();
        }
        
        if (token.isEmpty() || token.equalsIgnoreCase("null")) {
            return null;
        }
        
        return token;
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(String.format("{\"error\": \"Unauthorized\", \"message\": \"%s\"}", message));
    }
}
