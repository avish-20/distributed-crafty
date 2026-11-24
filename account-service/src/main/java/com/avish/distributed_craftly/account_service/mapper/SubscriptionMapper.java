package com.avish.distributed_craftly.account_service.mapper;

import com.avish.distributed_craftly.account_service.dto.subscription.SubscriptionResponse;
import com.avish.distributed_craftly.account_service.entity.Plan;
import com.avish.distributed_craftly.account_service.entity.Subscription;
import com.avish.distributed_craftly.common_lib.dto.PlanDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {
    SubscriptionResponse toSubscriptionResponse(Subscription subscription);
    PlanDto toPlanResponse(Plan plan);
}
