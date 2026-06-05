package com.spring.JavaT.auth;

import com.spring.JavaT.auth.dto.AuthResponse;
import com.spring.JavaT.auth.dto.ForgotPasswordRequest;
import com.spring.JavaT.auth.dto.LoginRequest;
import com.spring.JavaT.auth.dto.RefreshTokenRequest;
import com.spring.JavaT.auth.dto.RegisterRequest;
import com.spring.JavaT.auth.dto.ResendOtpRequest;
import com.spring.JavaT.auth.dto.ResetPasswordRequest;
import com.spring.JavaT.auth.dto.VerifyOtpRequest;
import com.spring.JavaT.common.ApiResponse;
import com.spring.JavaT.common.ResponseBuilder;
import com.spring.JavaT.common.validation.ValidationGroups;
import com.spring.JavaT.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints — most paths under {@code /api/v1/auth/**} are publicly accessible.
 * {@code POST /logout} requires a valid access token.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token, and OTP endpoints")
public class AuthController {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX        = "Bearer ";

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new customer account — public (no auth required)")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Validated(ValidationGroups.OnCreate.class) @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse response = authService.register(request);
        return ResponseBuilder.created(response, "Account created successfully", httpRequest);
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password — public (no auth required)")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Validated @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse response = authService.login(request);
        return ResponseBuilder.ok(response, "Login successful", httpRequest);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT token pair — public (no auth required)")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse response = authService.refreshToken(request);
        return ResponseBuilder.ok(response, "Token refreshed successfully", httpRequest);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and revoke access token — authenticated only")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = AUTHORIZATION_HEADER, required = false) String authHeader,
            HttpServletRequest httpRequest) {

        String accessToken = extractBearerToken(authHeader);
        authService.logout(accessToken);
        return ResponseBuilder.ok("Logged out successfully", httpRequest);
    }

    @PostMapping("/send-otp")
    @Operation(summary = "Send OTP to email (EMAIL_VERIFICATION or PASSWORD_RESET) — public")
    public ResponseEntity<ApiResponse<Void>> sendOtp(
            @Valid @RequestBody ResendOtpRequest request,
            HttpServletRequest httpRequest) {

        authService.sendOtp(request.getEmail(), request.getPurpose());
        return ResponseBuilder.ok(
                "If an account with that email exists, a verification code has been sent.",
                httpRequest);
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP code — public (no auth required)")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest) {

        authService.verifyOtp(request);
        return ResponseBuilder.ok("OTP verified successfully", httpRequest);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset link and OTP — public (no auth required)")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        authService.forgotPassword(request);
        return ResponseBuilder.ok(
                "If an account with that email exists, a password reset link and code have been sent.",
                httpRequest);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Complete password reset with email link token — public (no auth required)")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {

        authService.resetPassword(request);
        return ResponseBuilder.ok("Password has been reset successfully. Please log in.", httpRequest);
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Activate account via email verification link — public (no auth required)")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @RequestParam String token,
            HttpServletRequest httpRequest) {

        authService.verifyEmail(token);
        return ResponseBuilder.ok("Email verified successfully. You can now log in.", httpRequest);
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend email verification link — public (no auth required)")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        authService.resendVerificationEmail(request.getEmail());
        return ResponseBuilder.ok(
                "If an unverified account with that email exists, a new verification link has been sent.",
                httpRequest);
    }

    private String extractBearerToken(String authHeader) {
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        return authHeader.substring(BEARER_PREFIX.length());
    }
}
