package com.avish.distributed_craftly.account_service.dto.subscription;

import com.avish.distributed_craftly.common_lib.dto.PlanDto;

import java.time.Instant;

public record SubscriptionResponse(
        PlanDto plan,
        String status,
        Instant currentPeriodEnd,
        Long tokensUsedThisCycle
) {
}
