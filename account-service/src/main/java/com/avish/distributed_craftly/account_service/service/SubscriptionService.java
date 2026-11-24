package com.avish.distributed_craftly.account_service.service;



import com.avish.distributed_craftly.account_service.dto.subscription.SubscriptionResponse;
import com.avish.distributed_craftly.common_lib.dto.PlanDto;
import com.avish.distributed_craftly.common_lib.enums.SubscriptionStatus;

import java.time.Instant;

public interface SubscriptionService {
    SubscriptionResponse getCurrentSubscription();


    void activateSubscription(Long userId, Long planId, String subsCriptionId, String customerId);

    void updateSubscription(String subscriptionId, SubscriptionStatus status, Instant periodStart, Instant periodEnd, Boolean cancelAtPeriodEnd, Long planId);

    void cancelSubscription(String gatewaySubscriptionId);

    void renewSubscriptionPeriod(String subId, Instant periodStart, Instant periodEnd);

    void markSubscriptionPastDue(String subId);

    PlanDto getCurrentSubscribedPlanByUser();
}
