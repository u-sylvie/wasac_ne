package com.spring.JavaT.security;

import com.spring.JavaT.auth.RevokedTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

/**
 * Intercepts every request exactly once, extracts the JWT from the
 * {@code Authorization: Bearer <token>} header, validates it, and
 * populates the {@link SecurityContextHolder} if valid.
 *
 * <p>If the header is absent, malformed, or the token is invalid/expired,
 * the filter simply continues the chain without setting authentication.
 * Spring Security will then reject the request at the authorization layer
 * and invoke {@link SecurityEntryPoint} (401) or {@link AccessDeniedHandlerImpl} (403).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX        = "Bearer ";

    private final JwtService               jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final RevokedTokenRepository   revokedTokenRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only attempt authentication if the context is not already populated
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            tryAuthenticate(token, request);
        }

        filterChain.doFilter(request, response);
    }

    private void tryAuthenticate(String token, HttpServletRequest request) {
        try {
            if (jwtService.isRefreshToken(token)) {
                log.debug("Rejecting refresh token used as an access token");
                return;
            }

            String jti = jwtService.extractJti(token);
            if (StringUtils.hasText(jti) && revokedTokenRepository.existsByTokenJti(jti)) {
                log.debug("Rejecting revoked token with jti: {}", jti);
                return;
            }

            String email = jwtService.extractUsername(token);

            if (!StringUtils.hasText(email)) {
                return;
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (!jwtService.isTokenValid(token, userDetails)) {
                log.debug("JWT token is invalid or expired for user: {}", email);
                return;
            }

            if (!userDetails.isEnabled()) {
                log.debug("User account is not enabled: {}", email);
                return;
            }

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (Exception e) {
            // Log at debug level — invalid tokens are not server errors
            log.debug("Could not authenticate request: {}", e.getMessage());
        }
    }

    /**
     * Extracts the raw JWT string from the {@code Authorization} header.
     *
     * @return the token string, or {@code null} if the header is absent or malformed
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
