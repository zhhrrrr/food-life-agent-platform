package com.foodlife.trade.trigger.job;

import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTimeoutCompensateResult;
import com.foodlife.trade.domain.order.groupbuy.service.GroupBuyTimeoutCompensationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "food.trade.group-buy.timeout-compensation", name = "enabled", havingValue = "true")
public class GroupBuyTimeoutCompensationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupBuyTimeoutCompensationJob.class);

    private final GroupBuyTimeoutCompensationService groupBuyTimeoutCompensationService;

    public GroupBuyTimeoutCompensationJob(GroupBuyTimeoutCompensationService groupBuyTimeoutCompensationService) {
        this.groupBuyTimeoutCompensationService = groupBuyTimeoutCompensationService;
    }

    @Scheduled(fixedDelayString = "${food.trade.group-buy.timeout-compensation.fixed-delay-ms:60000}")
    public void compensateTimeoutTeams() {
        GroupBuyTimeoutCompensateResult result = groupBuyTimeoutCompensationService.compensateTimeoutTeams(null);
        if (result.getCompensatedTeamCount() > 0) {
            LOGGER.info("group buy timeout compensation completed, scannedTeamCount={}, compensatedTeamCount={}, canceledOrderCount={}, refundedOrderCount={}, restoredStockCount={}",
                    result.getScannedTeamCount(),
                    result.getCompensatedTeamCount(),
                    result.getCanceledOrderCount(),
                    result.getRefundedOrderCount(),
                    result.getRestoredStockCount());
        }
    }
}
