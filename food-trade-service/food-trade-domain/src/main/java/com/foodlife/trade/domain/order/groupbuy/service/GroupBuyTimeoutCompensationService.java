package com.foodlife.trade.domain.order.groupbuy.service;

import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTeamEntity;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTimeoutCompensateDetail;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTimeoutCompensateResult;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GroupBuyTimeoutCompensationService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final IGroupBuyRepository groupBuyRepository;

    public GroupBuyTimeoutCompensationService(IGroupBuyRepository groupBuyRepository) {
        this.groupBuyRepository = groupBuyRepository;
    }

    public GroupBuyTimeoutCompensateResult compensateTimeoutTeams(Integer limit) {
        LocalDateTime now = LocalDateTime.now();
        int normalizedLimit = normalizeLimit(limit);
        List<GroupBuyTeamEntity> timeoutTeams = groupBuyRepository.queryTimeoutInProgressTeams(now, normalizedLimit);

        GroupBuyTimeoutCompensateResult result = new GroupBuyTimeoutCompensateResult();
        result.setCompensateTime(now);
        result.setScannedTeamCount(timeoutTeams.size());
        result.setCompensatedTeamCount(0);
        result.setCanceledOrderCount(0);
        result.setRefundedOrderCount(0);
        result.setRestoredStockCount(0);

        for (GroupBuyTeamEntity team : timeoutTeams) {
            GroupBuyTimeoutCompensateDetail detail = groupBuyRepository.compensateTimeoutTeam(team.getTeamId(), now);
            if (detail == null) {
                continue;
            }
            result.getDetails().add(detail);
            result.setCompensatedTeamCount(result.getCompensatedTeamCount() + 1);
            result.setCanceledOrderCount(result.getCanceledOrderCount() + detail.getCanceledOrderCount());
            result.setRefundedOrderCount(result.getRefundedOrderCount() + detail.getRefundedOrderCount());
            result.setRestoredStockCount(result.getRestoredStockCount() + detail.getRestoredStockCount());
        }
        return result;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
