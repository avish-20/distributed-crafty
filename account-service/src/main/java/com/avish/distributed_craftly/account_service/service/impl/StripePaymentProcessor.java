package com.avish.distributed_craftly.account_service.service.impl;

import com.avish.distributed_craftly.account_service.dto.subscription.CheckoutRequest;
import com.avish.distributed_craftly.account_service.dto.subscription.CheckoutResponse;
import com.avish.distributed_craftly.account_service.dto.subscription.PortalResponse;
import com.avish.distributed_craftly.account_service.entity.Plan;
import com.avish.distributed_craftly.account_service.entity.User;
import com.avish.distributed_craftly.account_service.repository.PlanRepository;
import com.avish.distributed_craftly.account_service.repository.UserRepository;
import com.avish.distributed_craftly.account_service.service.PaymentProcessor;
import com.avish.distributed_craftly.account_service.service.SubscriptionService;
import com.avish.distributed_craftly.common_lib.enums.SubscriptionStatus;
import com.avish.distributed_craftly.common_lib.error.BadRequestException;
import com.avish.distributed_craftly.common_lib.error.ResourceNotFoundException;
import com.avish.distributed_craftly.common_lib.security.AuthUtil;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripePaymentProcessor implements PaymentProcessor {
    private final AuthUtil authUtil;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    @Value("${app.frontend.url}")
    private String frontEndUrl;

    @Override
    public CheckoutResponse createCheckoutSessionUrl(CheckoutRequest request) {
        Plan plan = planRepository.findById(request.planId()).orElseThrow(() ->
                new ResourceNotFoundException("Plan", request.planId().toString()));
        Long userId = authUtil.getCurrentUserId();
        User user = getUserById(userId);
        var params = SessionCreateParams.builder()
                .addLineItem(
                        SessionCreateParams.LineItem.builder().setPrice(plan.getStripePriceId()
                                ).setQuantity(1L).build())
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSubscriptionData(
                        new SessionCreateParams.SubscriptionData.Builder()
                                .setBillingMode(SessionCreateParams.SubscriptionData.BillingMode.builder()
                                        .setType(SessionCreateParams.SubscriptionData.BillingMode.Type.FLEXIBLE)
                                        .build())
                                .build()
                )
                .setSuccessUrl(frontEndUrl + "/success.html?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontEndUrl + "/cancel.html")
                .putMetadata("userId", userId.toString())
                .putMetadata("planId", plan.getId().toString());
        try {
            String stripeUserId = user.getStripeCustomerId();
            if(stripeUserId == null || stripeUserId.isBlank()) {
                params.setCustomerEmail(user.getUsername());
            }
            else{
                params.setCustomer(stripeUserId);
            }
            Session session = Session.create(params.build()); //
            return new CheckoutResponse(session.getUrl());
        }
        catch (StripeException e){
            throw new RuntimeException(e);
        }

    }



    @Override
    public PortalResponse openCustomerPortal() {
        Long userId = authUtil.getCurrentUserId();
        User user = getUserById(userId);
        String stripeCustomerId = user.getStripeCustomerId();
        if(stripeCustomerId == null || stripeCustomerId.isBlank()){
            throw new BadRequestException("User does not have a stripe customer id UserId: " + userId);
        }

        com.stripe.model.billingportal.Session portalSession = null;
        try {
            portalSession = com.stripe.model.billingportal.Session.create(
                    com.stripe.param.billingportal.SessionCreateParams.builder()
                            .setCustomer(stripeCustomerId)
                            .setReturnUrl(frontEndUrl)
                            .build()
            );
            return new PortalResponse(portalSession.getUrl());
        } catch (StripeException e) {
                throw new RuntimeException(e);
        }
    }

    @Override
    public void handleWebhookEvent(String type, StripeObject stripeObject, Map<String, String> metadata) {
        log.info("Handle stripe events: {}",type);

        switch (type) {
            case("checkout.session.completed") -> handleCheckoutSessionCompleted((Session)stripeObject, metadata);
            case("customer.subscription.updated") -> handleCustomerSubscriptionUpdated((Subscription) stripeObject);
            case("customer.subscription.deleted") -> handleCustomerSubscriptionDeleted((Subscription) stripeObject);
            case("invoice.paid") -> handleInvoicePaid((Invoice) stripeObject);
            case("invoice.payment_failed") -> handleInvoicePaymentFailed((Invoice) stripeObject);
            default -> log.debug("Unhandled stripe event: {}",type);
        }
    }

    private void handleInvoicePaymentFailed(Invoice invoice) {
        String subId = extractSubsCriptionId(invoice);
        if(subId == null) return;
        subscriptionService.markSubscriptionPastDue(subId);

    }

    private void handleInvoicePaid(Invoice invoice) {
        String subId = extractSubsCriptionId(invoice);
        if(subId == null) return;
        try{
            Subscription subscription = Subscription.retrieve(subId);//sdk calling stripe server
            var item = subscription.getItems().getData().get(0);
            Instant periodStart = toInstant(item.getCurrentPeriodStart());
            Instant periodEnd = toInstant(item.getCurrentPeriodEnd());
            subscriptionService.renewSubscriptionPeriod(subId, periodStart, periodEnd);
        }
        catch(Exception e){
            log.error("Error cancelling subscription", e);
            throw new RuntimeException(e);
        }
    }

    private void handleCustomerSubscriptionDeleted(Subscription subscription) {
        if(subscription == null){
            log.error("Session object was null");
            return;
        }

        subscriptionService.cancelSubscription(subscription.getId());
    }

    private void handleCustomerSubscriptionUpdated(Subscription subscription) {
        if(subscription == null){
            log.error("Session object was null");
            return;
        }
        SubscriptionStatus status = mapStripeStatusToEnum(subscription.getStatus());
        if(status == null){
            log.error("Unknown status '{}' for subscription {}", subscription.getStatus(), subscription.getId());
            return;
        }
        SubscriptionItem subscriptionItem = subscription.getItems().getData().get(0);
        Instant periodStart = toInstant(subscriptionItem.getCurrentPeriodStart());
        Instant periodEnd = toInstant(subscriptionItem.getCurrentPeriodEnd());

        Long PlanId = resolvePlanId(subscriptionItem.getPrice());

        subscriptionService.updateSubscription(subscription.getId(),status, periodStart, periodEnd, subscription.getCancelAtPeriodEnd(), PlanId);

    }



    private void handleCheckoutSessionCompleted(Session session, Map<String, String> metadata) {
        if(session == null){
            log.error("Session object was null");
            return;
        }
        Long userId = Long.parseLong(metadata.get("userId"));
        Long planId = Long.parseLong(metadata.get("planId"));

        String subsCriptionId = session.getSubscription();
        String customerId = session.getCustomer();
        User user = getUserById(userId);

        if(user.getStripeCustomerId() == null || user.getStripeCustomerId().isBlank()) {
            user.setStripeCustomerId(customerId);
            userRepository.save(user);
        }
        subscriptionService.activateSubscription(userId,planId,subsCriptionId, customerId);



    }
    private User getUserById(Long userId){
        return userRepository.findById(userId).orElseThrow(() ->
                new ResourceNotFoundException("User", userId.toString()));
    }
    private SubscriptionStatus mapStripeStatusToEnum(String status) {
        return switch (status) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trialing" -> SubscriptionStatus.TRAILING;   // Stripe spells it "trialing"
            case "canceled" -> SubscriptionStatus.CANCELLED;  // Stripe uses one 'l'
            case "past_due", "paused", "unpaid", "incomplete_expired" -> SubscriptionStatus.PAST_DUE;
            case "incomplete" -> SubscriptionStatus.INCOMPLETE;
            default -> {
                log.error("Unknown status '{}' for subscription", status);
                yield null;
            }
        };
    }
    public Instant toInstant(Long timestamp) {
        return Instant.ofEpochSecond(timestamp);
    }
    public Long resolvePlanId(Price price) {
        if(price == null){return null;}

        return planRepository.findByStripePriceId(price.getId())
                .map(Plan::getId)
                .orElse(null);

    }

    public String extractSubsCriptionId(Invoice invoice) {
        var parenet = invoice.getParent();
        if(parenet == null){
            log.error("Parent object was null");
            return null;
        }
        var subDetails = parenet.getSubscriptionDetails();
        if(subDetails == null){
            log.error("Subscription details were null");
            return null;
        }
        return subDetails.getSubscription();
    }
}
