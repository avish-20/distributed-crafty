package com.avish.distributed_craftly.account_service.dto.auth;

public record AuthResponse(String token, UserProfileResponse user) {
}
