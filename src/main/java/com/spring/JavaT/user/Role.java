package com.spring.JavaT.user;

/**
 * Application roles used for authorization.
 *
 * <p>Stored as strings in the database via {@code @Enumerated(EnumType.STRING)}.
 * Spring Security expects role names prefixed with {@code ROLE_} when using
 * {@code hasRole()} expressions — that prefix is added in {@link User#getAuthorities()}.
 */
public enum Role {

    /** Full administrative access. */
    ADMIN,

    /** Captures meter readings. */
    OPERATOR,

    /** Approves bills and payments. */
    FINANCE,

    /** Views bills and payment history. */
    CUSTOMER,

    /**
     * Backward-compatible roles kept from the original template.
     * These may exist in the database for previously created records.
     */
    USER,
    MODERATOR
}
