package com.spring.JavaT.auth;



import com.spring.JavaT.auth.dto.AuthResponse;

import com.spring.JavaT.auth.dto.ForgotPasswordRequest;

import com.spring.JavaT.auth.dto.LoginRequest;

import com.spring.JavaT.auth.dto.RefreshTokenRequest;

import com.spring.JavaT.auth.dto.RegisterRequest;

import com.spring.JavaT.auth.dto.ResetPasswordRequest;

import com.spring.JavaT.auth.dto.VerifyOtpRequest;

import com.spring.JavaT.common.EntityStatus;

import com.spring.JavaT.common.OtpPurpose;

import com.spring.JavaT.exception.BusinessException;

import com.spring.JavaT.exception.DuplicateResourceException;

import com.spring.JavaT.exception.ResourceNotFoundException;

import com.spring.JavaT.exception.UnauthorizedException;

import com.spring.JavaT.notification.EmailService;

import com.spring.JavaT.security.JwtProperties;

import com.spring.JavaT.security.JwtService;

import com.spring.JavaT.user.Role;

import com.spring.JavaT.user.User;

import com.spring.JavaT.user.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.HttpStatus;

import org.springframework.security.authentication.AuthenticationManager;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



import java.security.SecureRandom;

import java.time.Instant;

import java.time.temporal.ChronoUnit;

import java.util.Base64;

import java.util.Map;



/**

 * Handles user registration, login, token lifecycle, and OTP flows.

 *

 * <p>Registration and login return an {@link AuthResponse} containing access and refresh tokens

 * so the client is immediately authenticated after registering.

 */

@Service

@RequiredArgsConstructor

public class AuthService {



    private final UserRepository                     userRepository;

    private final PasswordEncoder                    passwordEncoder;

    private final JwtService                         jwtService;

    private final JwtProperties                      jwtProperties;

    private final AuthenticationManager              authenticationManager;

    private final AuthMapper                         authMapper;

    private final PasswordResetTokenRepository       passwordResetTokenRepository;

    private final EmailVerificationTokenRepository   emailVerificationTokenRepository;

    private final OtpTokenRepository                 otpTokenRepository;

    private final RevokedTokenRepository             revokedTokenRepository;

    private final EmailService                       emailService;



    private final SecureRandom secureRandom = new SecureRandom();



    @Value("${app.auth.password-reset-token-expiry-minutes:15}")

    private int passwordResetTokenExpiryMinutes;



    @Value("${app.auth.verification-token-expiry-hours:24}")

    private int verificationTokenExpiryHours;



    @Value("${app.auth.otp-expiry-minutes:10}")

    private int otpExpiryMinutes;



    // -------------------------------------------------------------------------

    // Registration

    // -------------------------------------------------------------------------



    /**

     * Creates a new user account and returns authentication tokens.

     *

     * @param request the registration payload

     * @return access and refresh tokens for the newly created user

     * @throws DuplicateResourceException if the email or username is already taken

     */

    @Transactional

    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {

            throw new DuplicateResourceException("User", "email", request.getEmail());

        }

        if (userRepository.existsByUsername(request.getUsername())) {

            throw new DuplicateResourceException("User", "username", request.getUsername());

        }



        User user = authMapper.toUser(request);

        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setRole(Role.CUSTOMER);

        user.setStatus(EntityStatus.PENDING);



        userRepository.save(user);



        issueAndSendVerificationToken(user);

        emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName());



        return buildAuthResponse(user);

    }



    // -------------------------------------------------------------------------

    // Login

    // -------------------------------------------------------------------------



    /**

     * Authenticates a user by email and password and returns tokens.

     *

     * @param request the login payload

     * @return access and refresh tokens

     */

    public AuthResponse login(LoginRequest request) {

        authenticationManager.authenticate(

                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())

        );



        User user = userRepository.findByEmail(request.getEmail())

                .orElseThrow(() -> new IllegalStateException("User not found after successful authentication"));



        if (EntityStatus.PENDING.equals(user.getStatus())) {

            throw new BusinessException(

                    "Email address not verified. Please check your inbox and click the verification link.",

                    HttpStatus.FORBIDDEN,

                    "EMAIL_NOT_VERIFIED"

            );

        }



        return buildAuthResponse(user);

    }



    // -------------------------------------------------------------------------

    // Token refresh & logout

    // -------------------------------------------------------------------------



    /**

     * Validates a refresh token and issues a new access/refresh token pair.

     *

     * @param request contains the refresh token

     * @return new authentication tokens

     */

    @Transactional

    public AuthResponse refreshToken(RefreshTokenRequest request) {

        String refreshToken = request.getRefreshToken();



        if (!jwtService.isRefreshToken(refreshToken)) {

            throw new UnauthorizedException("Invalid refresh token");

        }



        String jti = jwtService.extractJti(refreshToken);

        if (revokedTokenRepository.existsByTokenJti(jti)) {

            throw new UnauthorizedException("Refresh token has been revoked");

        }



        if (jwtService.isTokenExpired(refreshToken)) {

            throw new UnauthorizedException("Refresh token has expired");

        }



        String email = jwtService.extractUsername(refreshToken);

        User user = userRepository.findByEmail(email)

                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));



        if (!jwtService.isTokenValid(refreshToken, user)) {

            throw new UnauthorizedException("Invalid refresh token");

        }



        return buildAuthResponse(user);

    }



    /**

     * Blacklists the access token's {@code jti} until its natural expiry.

     *

     * @param accessToken the raw JWT from the Authorization header

     */

    @Transactional

    public void logout(String accessToken) {

        if (jwtService.isRefreshToken(accessToken)) {

            throw new UnauthorizedException("Cannot logout using a refresh token");

        }



        String jti = jwtService.extractJti(accessToken);

        if (revokedTokenRepository.existsByTokenJti(jti)) {

            return;

        }



        RevokedToken revokedToken = RevokedToken.builder()

                .tokenJti(jti)

                .expiresAt(jwtService.extractExpiration(accessToken).toInstant())

                .revokedAt(Instant.now())

                .build();



        revokedTokenRepository.save(revokedToken);

    }



    // -------------------------------------------------------------------------

    // OTP

    // -------------------------------------------------------------------------



    /**

     * Generates a six-digit OTP, stores it, and emails it to the user.

     *

     * <p>Always returns successfully even if the email is not found — prevents user enumeration.

     *

     * @param email   recipient email address

     * @param purpose why the OTP is being issued

     */

    @Transactional

    public void sendOtp(String email, OtpPurpose purpose) {

        userRepository.findByEmail(email).ifPresent(user -> {

            otpTokenRepository.deleteAllByUserAndPurpose(user, purpose);



            String code = generateOtpCode();

            OtpToken otpToken = OtpToken.builder()

                    .user(user)

                    .code(code)

                    .purpose(purpose)

                    .expiresAt(Instant.now().plus(otpExpiryMinutes, ChronoUnit.MINUTES))

                    .used(false)

                    .createdAt(Instant.now())

                    .build();



            otpTokenRepository.save(otpToken);

            emailService.sendOtpEmail(

                    user.getEmail(),

                    user.getFirstName(),

                    code,

                    otpPurposeLabel(purpose),

                    otpExpiryMinutes

            );

        });

    }



    /**

     * Validates an OTP and performs the action for the given purpose.

     *

     * @param request email, code, and purpose

     */

    @Transactional

    public void verifyOtp(VerifyOtpRequest request) {

        User user = userRepository.findByEmail(request.getEmail())

                .orElseThrow(() -> new ResourceNotFoundException("Invalid OTP code"));



        OtpToken otpToken = otpTokenRepository

                .findTopByUserAndPurposeAndUsedFalseOrderByCreatedAtDesc(user, request.getPurpose())

                .orElseThrow(() -> new ResourceNotFoundException("Invalid OTP code"));



        if (!otpToken.getCode().equals(request.getCode())) {

            throw new BusinessException("Invalid OTP code", HttpStatus.BAD_REQUEST, "INVALID_OTP");

        }



        if (otpToken.isExpiredOrUsed()) {

            throw new BusinessException(

                    "OTP code has expired or has already been used. Please request a new one.",

                    HttpStatus.BAD_REQUEST,

                    "OTP_EXPIRED"

            );

        }



        otpToken.setUsed(true);

        otpTokenRepository.save(otpToken);



        switch (request.getPurpose()) {

            case EMAIL_VERIFICATION -> {

                user.setStatus(EntityStatus.ACTIVE);

                userRepository.save(user);

            }

            case PASSWORD_RESET -> {

                // OTP verified — client may proceed to reset password via link or a follow-up step

            }

            case LOGIN -> throw new BusinessException(

                    "OTP login is not supported via this endpoint",

                    HttpStatus.BAD_REQUEST

            );

        }

    }



    // -------------------------------------------------------------------------

    // Token builder

    // -------------------------------------------------------------------------



    private AuthResponse buildAuthResponse(User user) {

        Map<String, Object> extraClaims = Map.of("role", user.getRole().name());



        String accessToken  = jwtService.generateAccessToken(extraClaims, user);

        String refreshToken = jwtService.generateRefreshToken(user);



        return AuthResponse.builder()

                .accessToken(accessToken)

                .refreshToken(refreshToken)

                .expiresIn(jwtProperties.getExpirationMs() / 1000)

                .email(user.getEmail())

                .role(user.getRole().name())

                .mustChangePassword(user.isMustChangePassword())

                .build();

    }



    // -------------------------------------------------------------------------

    // Password reset

    // -------------------------------------------------------------------------



    /**

     * Initiates a password reset by generating a link token and an OTP, then emailing both.

     *

     * <p>Always returns successfully even if the email is not found — prevents user enumeration.

     *

     * @param request contains the email address to reset

     */

    @Transactional

    public void forgotPassword(ForgotPasswordRequest request) {

        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {

            passwordResetTokenRepository.deleteAllByUser(user);



            String rawToken = generateSecureToken();

            PasswordResetToken resetToken = PasswordResetToken.builder()

                    .token(rawToken)

                    .user(user)

                    .expiresAt(Instant.now().plus(passwordResetTokenExpiryMinutes, ChronoUnit.MINUTES))

                    .build();



            passwordResetTokenRepository.save(resetToken);

            emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), rawToken);



            sendOtp(user.getEmail(), OtpPurpose.PASSWORD_RESET);

        });

    }



    /**

     * Completes a password reset by validating the token and updating the password.

     *

     * @param request contains the token and the new password

     */

    @Transactional

    public void resetPassword(ResetPasswordRequest request) {

        PasswordResetToken resetToken = passwordResetTokenRepository

                .findByToken(request.getToken())

                .orElseThrow(() -> new ResourceNotFoundException("Password reset token not found or already used"));



        if (resetToken.isExpiredOrUsed()) {

            throw new BusinessException(

                    "Password reset token has expired or has already been used. Please request a new one.",

                    HttpStatus.BAD_REQUEST

            );

        }



        User user = resetToken.getUser();

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        userRepository.save(user);



        resetToken.setUsed(true);

        passwordResetTokenRepository.save(resetToken);

    }



    // -------------------------------------------------------------------------

    // Email verification

    // -------------------------------------------------------------------------



    /**

     * Verifies a user's email address using the token from the verification email.

     *

     * @param token the raw token from the email link query parameter

     */

    @Transactional

    public void verifyEmail(String token) {

        EmailVerificationToken verificationToken = emailVerificationTokenRepository

                .findByToken(token)

                .orElseThrow(() -> new ResourceNotFoundException(

                        "Verification link is invalid or has already been used"));



        if (verificationToken.isExpired()) {

            throw new BusinessException(

                    "Verification link has expired. Please request a new one.",

                    HttpStatus.BAD_REQUEST,

                    "VERIFICATION_TOKEN_EXPIRED"

            );

        }



        User user = verificationToken.getUser();

        user.setStatus(EntityStatus.ACTIVE);

        userRepository.save(user);



        emailVerificationTokenRepository.delete(verificationToken);

    }



    /**

     * Resends the verification email for an unverified account.

     *

     * @param email the email address to resend to

     */

    @Transactional

    public void resendVerificationEmail(String email) {

        userRepository.findByEmail(email).ifPresent(user -> {

            if (EntityStatus.PENDING.equals(user.getStatus())) {

                emailVerificationTokenRepository.deleteByUser(user);

                issueAndSendVerificationToken(user);

            }

        });

    }



    // -------------------------------------------------------------------------

    // Private helpers

    // -------------------------------------------------------------------------



    private void issueAndSendVerificationToken(User user) {

        String rawToken = generateSecureToken();

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()

                .token(rawToken)

                .user(user)

                .expiresAt(Instant.now().plus(verificationTokenExpiryHours, ChronoUnit.HOURS))

                .build();



        emailVerificationTokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), rawToken);

    }



    private String generateSecureToken() {

        byte[] bytes = new byte[48];

        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    }



    private String generateOtpCode() {

        return String.format("%06d", secureRandom.nextInt(1_000_000));

    }



    private String otpPurposeLabel(OtpPurpose purpose) {

        return switch (purpose) {

            case EMAIL_VERIFICATION -> "verify your email address";

            case PASSWORD_RESET     -> "reset your password";

            case LOGIN              -> "complete your login";

        };

    }

}

