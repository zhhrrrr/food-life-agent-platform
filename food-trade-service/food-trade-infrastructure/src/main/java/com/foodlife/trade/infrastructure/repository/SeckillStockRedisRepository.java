package com.foodlife.trade.infrastructure.repository;

import com.foodlife.trade.domain.order.seckill.model.SeckillActivityEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockOccupyResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockPreheatResult;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillStockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Repository
public class SeckillStockRedisRepository implements ISeckillStockRepository {

    private static final String ACTIVITY_KEY_PREFIX = "food:trade:seckill:activity:";
    private static final String STOCK_KEY_PREFIX = "food:trade:seckill:stock:";
    private static final String USER_KEY_PREFIX = "food:trade:seckill:user:";
    private static final long EXTRA_TTL_SECONDS = 24 * 60 * 60;

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<List> occupyScript;
    private final DefaultRedisScript<List> releaseScript;

    public SeckillStockRedisRepository(StringRedisTemplate stringRedisTemplate,
                                       @Value("classpath:lua/seckill_stock_occupy.lua") Resource occupyScriptResource,
                                       @Value("classpath:lua/seckill_stock_release.lua") Resource releaseScriptResource) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.occupyScript = buildScript(occupyScriptResource);
        this.releaseScript = buildScript(releaseScriptResource);
    }

    @Override
    public SeckillStockPreheatResult preheatActivityStock(SeckillActivityEntity activity, LocalDateTime now) {
        String activityKey = activityKey(activity.getId());
        String stockKey = stockKey(activity.getId());
        String userKey = userKey(activity.getId());

        Map<String, String> activityCache = new HashMap<>();
        activityCache.put("activityId", String.valueOf(activity.getId()));
        activityCache.put("packageId", String.valueOf(activity.getPackageId()));
        activityCache.put("status", String.valueOf(activity.getActivityStatus()));
        activityCache.put("startTime", String.valueOf(toEpochSecond(activity.getValidStartTime())));
        activityCache.put("endTime", String.valueOf(toEpochSecond(activity.getValidEndTime())));
        activityCache.put("takeLimit", String.valueOf(activity.getUserTakeLimit()));

        stringRedisTemplate.opsForHash().putAll(activityKey, activityCache);
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(activity.getStock()));

        long ttlSeconds = calculateTtlSeconds(activity, now);
        stringRedisTemplate.expire(activityKey, ttlSeconds, TimeUnit.SECONDS);
        stringRedisTemplate.expire(stockKey, ttlSeconds, TimeUnit.SECONDS);
        stringRedisTemplate.expire(userKey, ttlSeconds, TimeUnit.SECONDS);

        SeckillStockPreheatResult result = new SeckillStockPreheatResult();
        result.setActivityId(activity.getId());
        result.setDbStock(activity.getStock());
        result.setRedisStock(activity.getStock());
        result.setStockKey(stockKey);
        result.setUserKey(userKey);
        return result;
    }

    @Override
    public SeckillStockOccupyResult occupyActivityStock(SeckillActivityEntity activity, Long userId, LocalDateTime now) {
        List<Long> scriptResult = stringRedisTemplate.execute(
                occupyScript,
                Arrays.asList(activityKey(activity.getId()), stockKey(activity.getId()), userKey(activity.getId())),
                String.valueOf(userId),
                String.valueOf(toEpochSecond(now)));

        long code = scriptResult == null || scriptResult.isEmpty() ? -1L : scriptResult.get(0);
        int remainingStock = scriptResult == null || scriptResult.size() < 2 ? -1 : scriptResult.get(1).intValue();
        return toOccupyResult(activity.getId(), userId, code, remainingStock);
    }

    @Override
    public void releaseActivityStock(Long activityId, Long userId) {
        stringRedisTemplate.execute(
                releaseScript,
                Arrays.asList(stockKey(activityId), userKey(activityId)),
                String.valueOf(userId));
    }

    @Override
    public void refreshActivityStock(SeckillActivityEntity activity, LocalDateTime now, Integer stock) {
        if (stock == null) {
            return;
        }
        String activityKey = activityKey(activity.getId());
        String stockKey = stockKey(activity.getId());

        Map<String, String> activityCache = new HashMap<>();
        activityCache.put("activityId", String.valueOf(activity.getId()));
        activityCache.put("packageId", String.valueOf(activity.getPackageId()));
        activityCache.put("status", String.valueOf(activity.getActivityStatus()));
        activityCache.put("startTime", String.valueOf(toEpochSecond(activity.getValidStartTime())));
        activityCache.put("endTime", String.valueOf(toEpochSecond(activity.getValidEndTime())));
        activityCache.put("takeLimit", String.valueOf(activity.getUserTakeLimit()));

        stringRedisTemplate.opsForHash().putAll(activityKey, activityCache);
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(stock));

        long ttlSeconds = calculateTtlSeconds(activity, now);
        stringRedisTemplate.expire(activityKey, ttlSeconds, TimeUnit.SECONDS);
        stringRedisTemplate.expire(stockKey, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Integer queryActivityStock(Long activityId) {
        String value = stringRedisTemplate.opsForValue().get(stockKey(activityId));
        return value == null ? null : Integer.valueOf(value);
    }

    private DefaultRedisScript<List> buildScript(Resource resource) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(resource));
        script.setResultType(List.class);
        return script;
    }

    private SeckillStockOccupyResult toOccupyResult(Long activityId, Long userId, long code, int remainingStock) {
        SeckillStockOccupyResult result = new SeckillStockOccupyResult();
        result.setActivityId(activityId);
        result.setUserId(userId);
        result.setSuccess(code == 1L);
        result.setRemainingStock(remainingStock);
        if (code == 1L) {
            result.setRejectCode(SeckillStockOccupyResult.SUCCESS);
            result.setRejectMessage("success");
            return result;
        }
        if (code == -2L) {
            result.setRejectCode(SeckillStockOccupyResult.ACTIVITY_DISABLED);
            result.setRejectMessage("seckill activity disabled");
        } else if (code == -3L) {
            result.setRejectCode(SeckillStockOccupyResult.ACTIVITY_NOT_START);
            result.setRejectMessage("seckill activity not start");
        } else if (code == -4L) {
            result.setRejectCode(SeckillStockOccupyResult.ACTIVITY_ENDED);
            result.setRejectMessage("seckill activity ended");
        } else if (code == -5L) {
            result.setRejectCode(SeckillStockOccupyResult.STOCK_NOT_ENOUGH);
            result.setRejectMessage("seckill stock not enough");
        } else if (code == -6L) {
            result.setRejectCode(SeckillStockOccupyResult.USER_TAKE_LIMIT);
            result.setRejectMessage("seckill user take limit");
        } else {
            result.setRejectCode(SeckillStockOccupyResult.ACTIVITY_NOT_PREHEATED);
            result.setRejectMessage("seckill activity stock not preheated");
        }
        return result;
    }

    private long calculateTtlSeconds(SeckillActivityEntity activity, LocalDateTime now) {
        long seconds = toEpochSecond(activity.getValidEndTime()) - toEpochSecond(now) + EXTRA_TTL_SECONDS;
        return Math.max(seconds, EXTRA_TTL_SECONDS);
    }

    private long toEpochSecond(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    private String activityKey(Long activityId) {
        return ACTIVITY_KEY_PREFIX + activityId;
    }

    private String stockKey(Long activityId) {
        return STOCK_KEY_PREFIX + activityId;
    }

    private String userKey(Long activityId) {
        return USER_KEY_PREFIX + activityId;
    }
}
