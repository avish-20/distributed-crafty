package com.avish.distributed_craftly.account_service.service;


import com.avish.distributed_craftly.account_service.dto.auth.AuthResponse;
import com.avish.distributed_craftly.account_service.dto.auth.LoginRequest;
import com.avish.distributed_craftly.account_service.dto.auth.SignupRequest;

public interface AuthService {

    AuthResponse signup(SignupRequest request);

    AuthResponse login(LoginRequest request);
}
