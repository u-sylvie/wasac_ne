package com.spring.JavaT.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.JavaT.common.ApiError;
import com.spring.JavaT.common.ApiResponse;
import com.spring.JavaT.user.User;
import com.spring.JavaT.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Blocks API access for users who must change their temporary password,
 * except for password change and logout endpoints.
 */
@Component
@RequiredArgsConstructor
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/v1/users/me/password",
            "/api/v1/auth/logout"
    );

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            Optional<User> userOpt = resolveUser(auth);
            if (userOpt.isPresent() && userOpt.get().isMustChangePassword()) {
                String path = request.getRequestURI();
                boolean allowed = ALLOWED_PATHS.stream().anyMatch(path::startsWith);
                if (!allowed) {
                    writeForbidden(response, path,
                            "You must change your temporary password before continuing.");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private Optional<User> resolveUser(Authentication auth) {
        if (auth.getPrincipal() instanceof User user) {
            return Optional.of(user);
        }
        return userRepository.findByEmail(auth.getName());
    }

    private void writeForbidden(HttpServletResponse response, String path, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .errors(List.of(ApiError.ofGlobal(message, "MUST_CHANGE_PASSWORD")))
                .path(path)
                .build();
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
