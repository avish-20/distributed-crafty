package com.avish.distributed_craftly.intelligence_service.client;

import com.avish.distributed_craftly.common_lib.dto.PlanDto;
import com.avish.distributed_craftly.common_lib.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@FeignClient(name = "account-service", path = "/account", url = "${ACCOUNT_SERVICE_URI:}")
public interface AccountClient {

    @GetMapping("/internal/v1/user/by-email")
    Optional<UserDto> getUserByEmail(@RequestParam("email") String email);

    @GetMapping("/internal/v1/billing/current-plan")
    PlanDto getCurrentSubscribedPlanByUser();
}
