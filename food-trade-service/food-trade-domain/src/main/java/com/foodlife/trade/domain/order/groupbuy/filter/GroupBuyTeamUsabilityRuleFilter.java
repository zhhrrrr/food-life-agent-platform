package com.foodlife.trade.domain.order.groupbuy.filter;

import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyStatusConstants;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTeamEntity;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyLockContext;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class GroupBuyTeamUsabilityRuleFilter implements GroupBuyLockRuleFilter {

    private final IGroupBuyRepository groupBuyRepository;

    public GroupBuyTeamUsabilityRuleFilter(IGroupBuyRepository groupBuyRepository) {
        this.groupBuyRepository = groupBuyRepository;
    }

    @Override
    public Void apply(GroupBuyLockContext requestParameter, GroupBuyLockContext dynamicContext) {
        String teamId = requestParameter.getCommand().getTeamId();
        if (teamId == null || teamId.trim().isEmpty()) {
            return next(requestParameter, dynamicContext);
        }
        GroupBuyTeamEntity team = groupBuyRepository.queryTeamByTeamId(teamId.trim());
        if (team == null) {
            throw new IllegalArgumentException("group buy team not found");
        }
        if (!dynamicContext.getActivity().getId().equals(team.getActivityId())) {
            throw new IllegalArgumentException("group buy team activity mismatch");
        }
        if (!GroupBuyStatusConstants.IN_PROGRESS.equals(team.getTeamStatus())) {
            throw new IllegalArgumentException("group buy team not in progress");
        }
        if (LocalDateTime.now().isAfter(team.getValidEndTime())) {
            throw new IllegalArgumentException("group buy team expired");
        }
        if (team.getLockCount() != null && team.getTargetCount() != null && team.getLockCount() >= team.getTargetCount()) {
            throw new IllegalArgumentException("group buy team full");
        }
        dynamicContext.setTeam(team);
        return next(requestParameter, dynamicContext);
    }
}
