package com.bank.users.user.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.UpdateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.User;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR,
        collectionMappingStrategy = CollectionMappingStrategy.TARGET_IMMUTABLE)
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "preferences", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(CreateUserRequest request);

    UserResponse toResponse(User user);

    @BeanMapping(ignoreByDefault = true,
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "displayName", conditionExpression = "java(request.hasField(\"displayName\"))")
    @Mapping(target = "email", conditionExpression = "java(request.hasField(\"email\"))")
    @Mapping(target = "phone", conditionExpression = "java(request.hasField(\"phone\"))")
    @Mapping(target = "preferences", conditionExpression = "java(request.hasField(\"preferences\"))")
    void update(UpdateUserRequest request, @MappingTarget User user);
}
