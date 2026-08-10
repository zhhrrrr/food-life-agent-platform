package com.foodlife.trade.domain.order.groupbuy.service;

import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTeamQueryResult;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTeamView;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyUserOrderQueryResult;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyUserOrderView;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GroupBuyQueryService {

    private static final int DEFAULT_TEAM_LIMIT = 20;
    private static final int MAX_TEAM_LIMIT = 50;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final IGroupBuyRepository groupBuyRepository;

    public GroupBuyQueryService(IGroupBuyRepository groupBuyRepository) {
        this.groupBuyRepository = groupBuyRepository;
    }

    public GroupBuyTeamQueryResult queryAvailableTeams(Long packageId, Integer limit) {
        if (packageId == null) {
            throw new IllegalArgumentException("packageId required");
        }
        GroupBuyTeamQueryResult result = new GroupBuyTeamQueryResult();
        result.setTeams(groupBuyRepository.listAvailableGroupBuyTeams(packageId, LocalDateTime.now(), normalizeLimit(limit)));
        return result;
    }

    public GroupBuyTeamView queryTeamDetail(String teamId) {
        if (teamId == null || teamId.trim().isEmpty()) {
            throw new IllegalArgumentException("teamId required");
        }
        GroupBuyTeamView team = groupBuyRepository.queryGroupBuyTeamDetail(teamId.trim(), LocalDateTime.now());
        if (team == null) {
            throw new IllegalArgumentException("group buy team not found");
        }
        return team;
    }

    public GroupBuyUserOrderQueryResult queryUserGroupBuyOrders(Long userId, Long lastId, Integer pageSize) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        int normalizedPageSize = normalizePageSize(pageSize);
        List<GroupBuyUserOrderView> orders = groupBuyRepository.listUserGroupBuyOrders(userId, lastId, normalizedPageSize + 1, LocalDateTime.now());
        boolean hasMore = orders.size() > normalizedPageSize;
        if (hasMore) {
            orders = orders.subList(0, normalizedPageSize);
        }

        GroupBuyUserOrderQueryResult result = new GroupBuyUserOrderQueryResult();
        result.setOrders(orders);
        result.setHasMore(hasMore);
        result.setLastId(orders.isEmpty() ? null : orders.get(orders.size() - 1).getGroupBuyOrderListId());
        return result;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_TEAM_LIMIT;
        }
        return Math.min(limit, MAX_TEAM_LIMIT);
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
