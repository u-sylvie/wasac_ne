package com.spring.JavaT.config;

import com.spring.JavaT.security.AccessDeniedHandlerImpl;
import com.spring.JavaT.security.JwtAuthenticationFilter;
import com.spring.JavaT.security.MustChangePasswordFilter;
import com.spring.JavaT.security.SecurityEntryPoint;
import com.spring.JavaT.security.SecurityProperties;
import com.spring.JavaT.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central Spring Security configuration.
 *
 * <p>Key decisions:
 * <ul>
 *   <li><b>Stateless sessions</b> — no {@code HttpSession} is created; every request
 *       must carry a valid JWT.</li>
 *   <li><b>CSRF disabled</b> — safe for stateless REST APIs that don't use cookies
 *       for authentication.</li>
 *   <li><b>Method security enabled</b> — {@code @PreAuthorize} / {@code @PostAuthorize}
 *       annotations work on service and controller methods.</li>
 *   <li><b>Custom 401/403 handlers</b> — return {@code ApiResponse} JSON instead of
 *       Spring's default HTML error pages.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter     jwtAuthFilter;
    private final MustChangePasswordFilter    mustChangePasswordFilter;
    private final UserDetailsServiceImpl      userDetailsService;
    private final SecurityEntryPoint       securityEntryPoint;
    private final AccessDeniedHandlerImpl  accessDeniedHandler;
    private final SecurityProperties       securityProperties;

    // -------------------------------------------------------------------------
    // Security filter chain
    // -------------------------------------------------------------------------

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — not needed for stateless JWT APIs
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless session — no HttpSession created or used
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Custom 401 / 403 responses
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(securityEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                    .requestMatchers(securityProperties.getPublicPaths().toArray(String[]::new)).permitAll()
                    .anyRequest().authenticated())

            // Wire the DaoAuthenticationProvider
            .authenticationProvider(authenticationProvider())

            // JWT filter runs before Spring's username/password filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(mustChangePasswordFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    // -------------------------------------------------------------------------
    // Authentication infrastructure
    // -------------------------------------------------------------------------

    /**
     * Wires our {@link UserDetailsServiceImpl} and {@link PasswordEncoder} into
     * Spring Security's authentication pipeline.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the {@link AuthenticationManager} as a bean so {@code AuthService}
     * can call it directly during login.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt with the default strength (10 rounds).
     * Increase the strength value for higher-security environments at the cost of CPU.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
