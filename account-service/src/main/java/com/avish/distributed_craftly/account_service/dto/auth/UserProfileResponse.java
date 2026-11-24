package com.avish.distributed_craftly.account_service.dto.auth;

public record UserProfileResponse(
        Long id,
        String username,
        String name
        ) {
}
