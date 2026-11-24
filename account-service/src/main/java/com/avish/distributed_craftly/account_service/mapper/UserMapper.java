package com.avish.distributed_craftly.account_service.mapper;


import com.avish.distributed_craftly.account_service.dto.auth.SignupRequest;
import com.avish.distributed_craftly.account_service.dto.auth.UserProfileResponse;
import com.avish.distributed_craftly.account_service.entity.User;
import com.avish.distributed_craftly.common_lib.dto.UserDto;
import com.avish.distributed_craftly.common_lib.security.JwtUserPrincipal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toEntity(SignupRequest signupRequest);
    @Mapping(source = "userId", target = "id")
    UserProfileResponse toUserProfileResponse(JwtUserPrincipal user);
    UserDto toUserDto(User user);
}
