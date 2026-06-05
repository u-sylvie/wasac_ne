package com.spring.JavaT.auth;

import com.spring.JavaT.auth.dto.RegisterRequest;
import com.spring.JavaT.user.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * MapStruct mapper for auth-related conversions.
 *
 * <p>Handles {@link RegisterRequest} → {@link User} so {@link AuthService}
 * doesn't need a manual builder. Fields that require special handling
 * (password hashing, role assignment) are intentionally excluded here and
 * set by the service after mapping.
 *
 * <p>{@code disableBuilder = true} forces MapStruct to use setter-based
 * mapping instead of Lombok's {@code @Builder}. This is required because
 * {@code User} extends {@link com.spring.JavaT.common.BaseEntity} and Lombok's
 * generated builder only covers fields declared directly on {@code User} —
 * inherited fields from {@code BaseEntity} are not included in the builder,
 * causing MapStruct to fail when trying to ignore them.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthMapper {

    /**
     * Maps a {@link RegisterRequest} to a new {@link User} entity.
     *
     * <p>Excluded fields — set by {@link AuthService} after this call:
     * <ul>
     *   <li>{@code password} — must be BCrypt-encoded before assignment</li>
     *   <li>{@code role}     — always {@link com.spring.JavaT.user.Role#USER} for self-registration</li>
     * </ul>
     * All {@link com.spring.JavaT.common.BaseEntity} audit fields (id, createdAt, etc.)
     * are excluded because they are managed by JPA and the auditing listener.
     */
    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "password",  ignore = true)
    @Mapping(target = "role",      ignore = true)
    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted",   ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "status",    ignore = true)
    @Mapping(target = "mustChangePassword", ignore = true)
    User toUser(RegisterRequest request);
}
