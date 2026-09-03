package com.foodlife.trade.trigger.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.foodlife.trade.trigger.sentinel.TradeSentinelResources;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class TradeSentinelRuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void initRules() {
        FlowRuleManager.loadRules(buildFlowRules());
        ParamFlowRuleManager.loadRules(buildParamFlowRules());
        DegradeRuleManager.loadRules(buildDegradeRules());
    }

    private List<FlowRule> buildFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        rules.add(qpsRule(TradeSentinelResources.NORMAL_ORDER_CREATE, 60));
        rules.add(qpsRule(TradeSentinelResources.GROUP_BUY_ORDER_CREATE, 60));
        rules.add(qpsRule(TradeSentinelResources.SECKILL_ORDER_CREATE, 20));
        rules.add(qpsRule(TradeSentinelResources.SECKILL_ORDER_ASYNC_CREATE, 40));
        rules.add(qpsRule(TradeSentinelResources.PAYMENT_CALLBACK, 100));
        rules.add(qpsRule(TradeSentinelResources.BUSINESS_PACKAGE_SNAPSHOT, 200));
        rules.add(qpsRule(TradeSentinelResources.BUSINESS_PACKAGE_STOCK, 120));
        return rules;
    }

    private FlowRule qpsRule(String resource, double count) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(count);
    }

    private List<ParamFlowRule> buildParamFlowRules() {
        List<ParamFlowRule> rules = new ArrayList<>();
        rules.add(new ParamFlowRule(TradeSentinelResources.USER_ORDER_CREATE)
                .setParamIdx(0)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(5)
                .setDurationInSec(60));
        rules.add(new ParamFlowRule(TradeSentinelResources.SECKILL_STOCK_OCCUPY)
                .setParamIdx(0)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(20)
                .setDurationInSec(1));
        rules.add(new ParamFlowRule(TradeSentinelResources.BUSINESS_PACKAGE_STOCK)
                .setParamIdx(0)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(30)
                .setDurationInSec(1));
        return rules;
    }

    private List<DegradeRule> buildDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();
        rules.add(exceptionRatioRule(TradeSentinelResources.NORMAL_ORDER_CREATE, 0.5, 10, 10, 10));
        rules.add(exceptionRatioRule(TradeSentinelResources.GROUP_BUY_ORDER_CREATE, 0.5, 10, 10, 10));
        rules.add(exceptionRatioRule(TradeSentinelResources.SECKILL_ORDER_CREATE, 0.4, 10, 10, 10));
        rules.add(exceptionRatioRule(TradeSentinelResources.SECKILL_ORDER_ASYNC_CREATE, 0.4, 10, 10, 10));
        rules.add(exceptionRatioRule(TradeSentinelResources.PAYMENT_CALLBACK, 0.5, 10, 10, 10));
        rules.add(exceptionRatioRule(TradeSentinelResources.BUSINESS_PACKAGE_SNAPSHOT, 0.5, 5, 10, 10));
        rules.add(exceptionRatioRule(TradeSentinelResources.BUSINESS_PACKAGE_STOCK, 0.5, 5, 10, 10));
        return rules;
    }

    private DegradeRule exceptionRatioRule(String resource,
                                           double exceptionRatio,
                                           int minRequestAmount,
                                           int statIntervalSeconds,
                                           int timeWindowSeconds) {
        return new DegradeRule(resource)
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                .setCount(exceptionRatio)
                .setMinRequestAmount(minRequestAmount)
                .setStatIntervalMs(statIntervalSeconds * 1000)
                .setTimeWindow(timeWindowSeconds);
    }
}
