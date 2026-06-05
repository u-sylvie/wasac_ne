package com.spring.JavaT.user;

import com.spring.JavaT.audit.AuditService;
import com.spring.JavaT.common.EntityStatus;
import com.spring.JavaT.common.TempPasswordGenerator;
import com.spring.JavaT.common.filter.BaseSpecification;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.exception.BusinessException;
import com.spring.JavaT.exception.DuplicateResourceException;
import com.spring.JavaT.exception.ForbiddenException;
import com.spring.JavaT.exception.ResourceNotFoundException;
import com.spring.JavaT.notification.EmailService;
import com.spring.JavaT.user.dto.AdminCreateUserRequest;
import com.spring.JavaT.user.dto.UpdatePasswordRequest;
import com.spring.JavaT.user.dto.UpdateProfileRequest;
import com.spring.JavaT.user.dto.UpdateRoleRequest;
import com.spring.JavaT.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
/**
 * Business logic for all user management operations.
 *
 * <p>Authorization rules are enforced at two levels:
 * <ul>
 *   <li>Coarse-grained — {@code @PreAuthorize} on the controller methods.</li>
 *   <li>Fine-grained — ownership checks inside service methods (a USER can only
 *       modify their own profile, never another user's).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper      userMapper;
    private final EmailService    emailService;
    private final AuditService    auditService;

    // -------------------------------------------------------------------------
    // Profile — self-service
    // -------------------------------------------------------------------------

    /**
     * Returns the profile of the currently authenticated user.
     *
     * @param email the email of the authenticated principal (from JWT subject)
     */
    @Transactional(readOnly = true)
    public UserDto getMyProfile(String email) {
        return userMapper.toDto(findByEmailOrThrow(email));
    }

    /**
     * Updates the authenticated user's own profile fields.
     * Only non-null fields in the request are applied (partial update).
     *
     * @param email   the email of the authenticated principal
     * @param request fields to update
     */
    @Transactional
    public UserDto updateMyProfile(String email, UpdateProfileRequest request) {
        User user = findByEmailOrThrow(email);

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName().strip());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName().strip());
        }
        if (request.getUsername() != null) {
            String newUsername = request.getUsername().strip();
            if (!newUsername.equals(user.getDisplayUsername())) {
                if (userRepository.existsByUsername(newUsername)) {
                    throw new DuplicateResourceException("User", "username", newUsername);
                }
                user.setUsername(newUsername);
            }
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().strip());
        }

        return userMapper.toDto(userRepository.save(user));
    }

    /**
     * Changes the authenticated user's password after verifying the current one.
     *
     * @param email   the email of the authenticated principal
     * @param request current and new password
     * @throws ForbiddenException if the current password is wrong
     */
    @Transactional
    public void updateMyPassword(String email, UpdatePasswordRequest request) {
        User user = findByEmailOrThrow(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ForbiddenException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        auditService.log("User", user.getId(), "UPDATE", email, "Password changed");
    }

    // -------------------------------------------------------------------------
    // Admin — user management
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated, optionally filtered list of all users. Admin only.
     *
     * @param criteria list of filter conditions (empty = no filter)
     * @param pageable pagination and sort
     */
    @Transactional(readOnly = true)
    public Page<UserDto> getAllUsers(List<SearchCriteria> criteria, Pageable pageable) {
        Specification<User> spec = new BaseSpecification<>(criteria);
        return userRepository.findAll(spec, pageable).map(userMapper::toDto);
    }

    /**
     * Returns a single user by ID. Admin only.
     *
     * @throws ResourceNotFoundException if no user with that ID exists
     */
    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {
        return userMapper.toDto(findByIdOrThrow(id));
    }

    /**
     * Changes a user's role. Admin only.
     *
     * @param id      the target user's ID
     * @param request the new role
     * @throws ResourceNotFoundException if no user with that ID exists
     */
    @Transactional
    public UserDto updateRole(Long id, UpdateRoleRequest request, String adminEmail) {
        User user = findByIdOrThrow(id);
        Role oldRole = user.getRole();
        Role newRole = Role.valueOf(request.getRole());
        if (oldRole == newRole) {
            return userMapper.toDto(user);
        }
        user.setRole(newRole);
        User saved = userRepository.save(user);
        emailService.sendRoleChangeEmail(saved.getEmail(), saved.getFirstName(), oldRole.name(), newRole.name());
        auditService.log("User", saved.getId(), "UPDATE", adminEmail,
                "Role changed from " + oldRole + " to " + newRole);
        return userMapper.toDto(saved);
    }

    /**
     * Creates an operator or finance user with a temporary password emailed to them.
     */
    @Transactional
    public UserDto createUserByAdmin(AdminCreateUserRequest request, String adminEmail) {
        Role role = Role.valueOf(request.getRole().toUpperCase());
        if (role != Role.OPERATOR && role != Role.FINANCE) {
            throw new BusinessException("Only OPERATOR and FINANCE users can be created by an administrator",
                    HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByEmail(request.getEmail().strip().toLowerCase())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername().strip())) {
            throw new DuplicateResourceException("User", "username", request.getUsername());
        }

        String tempPassword = TempPasswordGenerator.generate(12);
        User user = User.builder()
                .firstName(request.getFirstName().strip())
                .lastName(request.getLastName().strip())
                .username(request.getUsername().strip())
                .email(request.getEmail().strip().toLowerCase())
                .phone(request.getPhone().strip())
                .password(passwordEncoder.encode(tempPassword))
                .role(role)
                .mustChangePassword(true)
                .build();

        User saved = userRepository.save(user);
        emailService.sendUserCredentialsEmail(
                saved.getEmail(), saved.getFirstName(), saved.getEmail(), tempPassword, role.name());
        auditService.log("User", saved.getId(), "CREATE", adminEmail,
                "Admin created " + role + " user " + saved.getEmail());
        return userMapper.toDto(saved);
    }

    /**
     * Soft-deactivates a user account. Admin only.
     *
     * <p>Sets {@code deleted = true} and {@code status = INACTIVE} via
     * {@link User#softDelete(String)}. The user's JWT will still be structurally
     * valid until it expires, but {@link User#isEnabled()} returns {@code false}
     * so Spring Security will reject their requests on the next DB-backed check.
     *
     * @param id           the target user's ID
     * @param adminEmail   the email of the admin performing the action (for audit trail)
     * @throws ResourceNotFoundException if no user with that ID exists
     */
    @Transactional
    public UserDto deactivateUser(Long id, String adminEmail) {
        User user = findByIdOrThrow(id);
        user.softDelete(adminEmail);
        return userMapper.toDto(userRepository.save(user));
    }

    /**
     * Restores a soft-deactivated user account. Admin only.
     *
     * @param id the target user's ID
     * @throws ResourceNotFoundException if no user with that ID exists
     */
    @Transactional
    public UserDto activateUser(Long id) {
        User user = findByIdOrThrow(id);
        user.restore();
        return userMapper.toDto(userRepository.save(user));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private User findByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private User findByIdOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }
}
