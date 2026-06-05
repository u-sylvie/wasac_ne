package com.spring.JavaT.user;

import com.spring.JavaT.common.BaseEntity;
import com.spring.JavaT.common.EntityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Core user entity. Extends {@link BaseEntity} for id, timestamps, soft-delete,
 * and status, and implements {@link UserDetails} so Spring Security can use it
 * directly without an adapter class.
 *
 * <p>Account locking and expiry are delegated to {@link EntityStatus}:
 * only {@code ACTIVE} users are considered enabled and non-locked.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity implements UserDetails {

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    /** Must be unique across the table — enforced by a DB UNIQUE constraint. */
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /** Must be unique across the table — enforced by a DB UNIQUE constraint. */
    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    /** Phone number (for customer/operator/finance contact). */
    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    /** BCrypt-hashed password. Never store plain text. */
    @Column(name = "password", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    /** When true, user must change password before accessing the system. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    // -------------------------------------------------------------------------
    // UserDetails contract
    // -------------------------------------------------------------------------

    /**
     * Returns a single authority derived from the user's {@link Role}.
     * The {@code ROLE_} prefix is required by Spring Security's {@code hasRole()} expressions.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * Returns the email address as the Spring Security principal identifier.
     *
     * <p>This is intentionally the email, not the {@code username} field, because:
     * <ul>
     *   <li>Login is done by email.</li>
     *   <li>The JWT {@code sub} claim is set from this value.</li>
     *   <li>{@link com.spring.JavaT.security.UserDetailsServiceImpl} loads users by email.</li>
     * </ul>
     * All three must use the same identifier or token validation silently fails.
     * Use {@link #getDisplayUsername()} when you need the human-readable username.
     */
    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Returns the user-chosen display username (e.g. "johndoe").
     * Use this anywhere you need the username field, not the security principal.
     */
    public String getDisplayUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    /**
     * Account is considered non-expired, non-locked, and enabled only when
     * the entity status is {@link EntityStatus#ACTIVE} and not soft-deleted.
     */
    @Override
    public boolean isAccountNonExpired() {
        return isActive();
    }

    @Override
    public boolean isAccountNonLocked() {
        return !EntityStatus.SUSPENDED.equals(getStatus());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive();
    }
}
