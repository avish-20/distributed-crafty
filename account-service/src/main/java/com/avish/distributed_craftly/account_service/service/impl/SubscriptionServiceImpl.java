package com.avish.distributed_craftly.account_service.service.impl;


import com.avish.distributed_craftly.account_service.dto.subscription.SubscriptionResponse;
import com.avish.distributed_craftly.account_service.entity.Plan;
import com.avish.distributed_craftly.account_service.entity.Subscription;
import com.avish.distributed_craftly.account_service.entity.User;
import com.avish.distributed_craftly.account_service.mapper.SubscriptionMapper;
import com.avish.distributed_craftly.account_service.repository.PlanRepository;
import com.avish.distributed_craftly.account_service.repository.SubscriptionRepository;
import com.avish.distributed_craftly.account_service.repository.UserRepository;
import com.avish.distributed_craftly.account_service.service.SubscriptionService;
import com.avish.distributed_craftly.common_lib.dto.PlanDto;
import com.avish.distributed_craftly.common_lib.enums.SubscriptionStatus;
import com.avish.distributed_craftly.common_lib.error.ResourceNotFoundException;
import com.avish.distributed_craftly.common_lib.security.AuthUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {
    private final AuthUtil authUtil;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;;

    @Override
    public SubscriptionResponse getCurrentSubscription() {
        long userId = authUtil.getCurrentUserId();
        var subscription  = subscriptionRepository.findByUserIdAndStatusIn(
                userId, Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE, SubscriptionStatus.TRAILING))
                .orElse(
                        new Subscription()
                );

        return subscriptionMapper.toSubscriptionResponse(subscription);


    }

    @Override
    public void activateSubscription(Long userId, Long planId, String subscriptionId, String customerId) {
        boolean exist = subscriptionRepository.existsByStripeSubscriptionId(subscriptionId);
        if(exist) return;

        User user = getUserById(userId);
        Plan plan = getPlanById(planId);

        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(plan)
                .stripeSubscriptionId(subscriptionId)
                .status(SubscriptionStatus.INCOMPLETE)
                .build();

        subscriptionRepository.save(subscription);

    }

    @Override
    @Transactional
    public void updateSubscription(String subscriptionId, SubscriptionStatus status, Instant periodStart, Instant periodEnd, Boolean cancelAtPeriodEnd, Long planId) {
        Subscription subscription = getSubscription(subscriptionId);

        boolean hasSubscriptionUpdated = false;
        if(null != status && subscription.getStatus() != status) {
            subscription.setStatus(status);
            hasSubscriptionUpdated = true;
        }
        if(null != periodStart && subscription.getCurrentPeriodStart() != periodStart) {
            subscription.setCurrentPeriodStart(periodStart);
            hasSubscriptionUpdated = true;
        }
        if(null != periodEnd && subscription.getCurrentPeriodEnd() != periodEnd) {
            subscription.setCurrentPeriodEnd(periodEnd);
            hasSubscriptionUpdated = true;
        }
        if(null != cancelAtPeriodEnd && subscription.getCancelAtPeriodEnd() != cancelAtPeriodEnd) {
            subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd);
            hasSubscriptionUpdated = true;
        }
        if(null != planId && subscription.getPlan().getId() != planId) {
            Plan plan = getPlanById(planId);
            subscription.setPlan(plan);
        }
        if(hasSubscriptionUpdated) {
            log.debug("Subscription updated {}",subscriptionId);
            subscriptionRepository.save(subscription);
        }

    }

    @Override
    public void cancelSubscription(String gatewaySubscriptionId) {
        Subscription subscription = getSubscription(gatewaySubscriptionId);
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.save(subscription);

    }

    @Override
    public void renewSubscriptionPeriod(String gatewaySubscriptionId, Instant periodStart, Instant periodEnd) {
        Subscription subscription = getSubscription(gatewaySubscriptionId);
        Instant newStart = periodStart!=null? periodStart:subscription.getCurrentPeriodEnd();
        subscription.setCurrentPeriodStart(newStart);
        subscription.setCurrentPeriodEnd(periodEnd);
        if(subscription.getStatus()==SubscriptionStatus.PAST_DUE || subscription.getStatus()==SubscriptionStatus.INCOMPLETE)
            subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(subscription);

    }


    @Override
    public void markSubscriptionPastDue(String subId) {
        Subscription subscription = getSubscription(subId);

        if(subscription.getStatus() == SubscriptionStatus.PAST_DUE){
            log.debug("Subscription is already past due");

            return;
        }
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        subscriptionRepository.save(subscription);

        //notify user
    }

    @Override
    public PlanDto getCurrentSubscribedPlanByUser() {
        SubscriptionResponse subscription = getCurrentSubscription();
        return subscription.plan();

    }

    private final Integer FREE_TIER_ALLOWED_PROJECTS = 100;


    //Utility methods
    private User getUserById(Long userId) {
        return userRepository.findById(userId).orElseThrow(
                ()-> new ResourceNotFoundException("User", userId.toString())
        );
    }

    private Plan getPlanById(Long planId) {
        return planRepository.findById(planId).orElseThrow(
                ()-> new ResourceNotFoundException("Plan", planId.toString())
        );
    }
    private @NonNull Subscription getSubscription(String gatewaySubscriptionId) {
        return subscriptionRepository.findByStripeSubscriptionId(gatewaySubscriptionId).orElseThrow(
                () -> new ResourceNotFoundException("Subscription", gatewaySubscriptionId)
        );
    }


}
