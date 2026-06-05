package com.spring.JavaT.security;

/**
 * Distinguishes access tokens from refresh tokens via the {@code tokenType} JWT claim.
 */
public enum TokenType {
    ACCESS,
    REFRESH
}
