package com.avish.distributed_craftly.account_service.repository;

import com.avish.distributed_craftly.account_service.entity.User;
import com.stripe.net.HttpHeaders;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameIgnoreCase(String email);
}
