package com.spring.JavaT.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Sends emails asynchronously using Spring's {@link JavaMailSender}.
 *
 * <p>All public methods are annotated with {@code @Async("emailTaskExecutor")}
 * so they execute on the dedicated email thread pool defined in
 * {@link com.spring.JavaT.config.AsyncConfig}. The calling thread returns
 * immediately — email delivery happens in the background.
 *
 * <p>HTML templates live in {@code src/main/resources/templates/email/}.
 * Variables are substituted using a simple {@code {{placeholder}}} syntax
 * via {@link #loadTemplate(String, Map)} — no template engine dependency needed.
 *
 * <p>Failures are logged but not re-thrown to the caller. If you need
 * retry logic, replace the catch block with a message queue (e.g. RabbitMQ).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    // -------------------------------------------------------------------------
    // Generic send — the reusable core
    // -------------------------------------------------------------------------

    /**
     * Sends an email asynchronously.
     *
     * <p>This is the single entry point for all email delivery. Every named
     * method below (verification, password reset, etc.) builds an
     * {@link EmailRequest} and delegates here.
     *
     * @param request all email data (to, subject, body, html flag)
     */
    @Async("emailTaskExecutor")
    public void send(EmailRequest request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(
                    new InternetAddress(mailProperties.getFrom(), mailProperties.getFromName())
            );

            if (request.getToName() != null) {
                helper.setTo(new InternetAddress(request.getTo(), request.getToName()));
            } else {
                helper.setTo(request.getTo());
            }

            helper.setSubject(request.getSubject());
            helper.setText(request.getBody(), request.isHtml());

            mailSender.send(message);
            log.info("Email sent to [{}] subject=[{}]", request.getTo(), request.getSubject());

        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send email to [{}]: {}", request.getTo(), e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Named email types — add new ones here as the app grows
    // -------------------------------------------------------------------------

    /**
     * Sends an account verification email with a clickable confirmation link.
     *
     * @param toEmail   recipient email address
     * @param firstName recipient's first name for personalisation
     * @param token     the verification token (appended to the confirmation URL)
     */
    @Async("emailTaskExecutor")
    public void sendVerificationEmail(String toEmail, String firstName, String token) {
        String confirmUrl = mailProperties.getBaseUrl()
                + "/api/v1/auth/verify-email?token=" + token;

        String body = loadTemplate("verification.html", Map.of(
                "appName",    mailProperties.getFromName(),
                "firstName",  firstName,
                "confirmUrl", confirmUrl
        ));

        send(EmailRequest.builder()
                .to(toEmail)
                .toName(firstName)
                .subject("Verify your " + mailProperties.getFromName() + " account")
                .body(body)
                .html(true)
                .build());
    }

    /**
     * Sends a password reset email with a time-limited reset link.
     *
     * @param toEmail   recipient email address
     * @param firstName recipient's first name for personalisation
     * @param token     the password reset token (appended to the reset URL)
     */
    /**
     * Sends a welcome email after successful registration.
     *
     * @param toEmail   recipient email address
     * @param firstName recipient's first name for personalisation
     */
    @Async("emailTaskExecutor")
    public void sendWelcomeEmail(String toEmail, String firstName) {
        String body = loadTemplate("welcome.html", Map.of(
                "appName",   mailProperties.getFromName(),
                "firstName", firstName
        ));

        send(EmailRequest.builder()
                .to(toEmail)
                .toName(firstName)
                .subject("Welcome to " + mailProperties.getFromName())
                .body(body)
                .html(true)
                .build());
    }

    /**
     * Sends a one-time password email for verification or password reset.
     *
     * @param toEmail        recipient email address
     * @param firstName      recipient's first name for personalisation
     * @param otpCode        the six-digit OTP code
     * @param purposeLabel   human-readable purpose (e.g. "verify your email")
     * @param expiryMinutes  how long the code remains valid
     */
    @Async("emailTaskExecutor")
    public void sendOtpEmail(String toEmail, String firstName, String otpCode,
                             String purposeLabel, int expiryMinutes) {
        String body = loadTemplate("otp.html", Map.of(
                "appName",       mailProperties.getFromName(),
                "firstName",     firstName,
                "otpCode",       otpCode,
                "purposeLabel",  purposeLabel,
                "expiryMinutes", String.valueOf(expiryMinutes)
        ));

        send(EmailRequest.builder()
                .to(toEmail)
                .toName(firstName)
                .subject("Your " + mailProperties.getFromName() + " verification code")
                .body(body)
                .html(true)
                .build());
    }

  /**
   * Sends a bill-generated notification email to a customer.
   */
  @Async("emailTaskExecutor")
  public void sendBillNotificationEmail(String toEmail, String firstName, String billReference,
                                        String totalAmount, int billingMonth, int billingYear) {
    String body = loadTemplate("bill-notification.html", Map.of(
        "appName", mailProperties.getFromName(),
        "firstName", firstName,
        "billReference", billReference,
        "totalAmount", totalAmount,
        "billingMonth", String.valueOf(billingMonth),
        "billingYear", String.valueOf(billingYear)
    ));

    send(EmailRequest.builder()
        .to(toEmail)
        .toName(firstName)
        .subject("Your utility bill " + billReference + " is ready")
        .body(body)
        .html(true)
        .build());
  }

  /**
   * Sends a payment-completed notification email to a customer.
   */
  @Async("emailTaskExecutor")
  public void sendPaymentNotificationEmail(String toEmail, String firstName, String billReference,
                                           String totalAmount) {
    String body = loadTemplate("payment-notification.html", Map.of(
        "appName", mailProperties.getFromName(),
        "firstName", firstName,
        "billReference", billReference,
        "totalAmount", totalAmount
    ));

    send(EmailRequest.builder()
        .to(toEmail)
        .toName(firstName)
        .subject("Payment received for bill " + billReference)
        .body(body)
        .html(true)
        .build());
  }

    @Async("emailTaskExecutor")
    public void sendUserCredentialsEmail(String toEmail, String firstName, String email,
                                         String temporaryPassword, String role) {
        String loginUrl = mailProperties.getBaseUrl() + "/swagger-ui.html";
        String body = loadTemplate("user-credentials.html", Map.of(
                "appName", mailProperties.getFromName(),
                "firstName", firstName,
                "email", email,
                "temporaryPassword", temporaryPassword,
                "role", role,
                "loginUrl", loginUrl
        ));

        send(EmailRequest.builder()
                .to(toEmail)
                .toName(firstName)
                .subject("Your WASAC system login credentials")
                .body(body)
                .html(true)
                .build());
    }

    @Async("emailTaskExecutor")
    public void sendRoleChangeEmail(String toEmail, String firstName, String oldRole, String newRole) {
        String loginUrl = mailProperties.getBaseUrl() + "/swagger-ui.html";
        String body = loadTemplate("role-change.html", Map.of(
                "appName", mailProperties.getFromName(),
                "firstName", firstName,
                "oldRole", oldRole,
                "newRole", newRole,
                "roleDescription", describeRole(newRole),
                "loginUrl", loginUrl
        ));

        send(EmailRequest.builder()
                .to(toEmail)
                .toName(firstName)
                .subject("Your WASAC access role has been updated")
                .body(body)
                .html(true)
                .build());
    }

    private String describeRole(String role) {
        return switch (role) {
            case "ADMIN" -> "You can manage users, tariffs, and system configuration.";
            case "OPERATOR" -> "You can register customers, meters, and record meter readings.";
            case "FINANCE" -> "You can approve bills and record customer payments.";
            case "CUSTOMER" -> "You can view your bills and payment history.";
            default -> "Please sign in to review your updated permissions.";
        };
    }

    @Async("emailTaskExecutor")
    public void sendPasswordResetEmail(String toEmail, String firstName, String token) {
        String resetUrl = mailProperties.getBaseUrl()
                + "/api/v1/auth/reset-password?token=" + token;

        String body = loadTemplate("password-reset.html", Map.of(
                "appName",   mailProperties.getFromName(),
                "firstName", firstName,
                "resetUrl",  resetUrl
        ));

        send(EmailRequest.builder()
                .to(toEmail)
                .toName(firstName)
                .subject("Reset your " + mailProperties.getFromName() + " password")
                .body(body)
                .html(true)
                .build());
    }

    // -------------------------------------------------------------------------
    // Template loader
    // -------------------------------------------------------------------------

    /**
     * Loads an HTML template from {@code classpath:templates/email/<name>}
     * and substitutes all {@code {{key}}} placeholders with the provided values.
     *
     * <p>This is intentionally simple — no template engine dependency.
     * If you need conditionals, loops, or inheritance, add Thymeleaf or
     * Freemarker and replace this method.
     *
     * @param templateName filename inside {@code templates/email/} (e.g. {@code "verification.html"})
     * @param variables    map of placeholder name → replacement value
     * @return the rendered HTML string
     */
    private String loadTemplate(String templateName, Map<String, String> variables) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/email/" + templateName);
            String content = resource.getContentAsString(StandardCharsets.UTF_8);

            for (Map.Entry<String, String> entry : variables.entrySet()) {
                content = content.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }

            return content;

        } catch (IOException e) {
            log.error("Failed to load email template [{}]: {}", templateName, e.getMessage());
            // Fallback: return a plain-text body so the email still goes out
            return variables.getOrDefault("body", "Please contact support.");
        }
    }
}
