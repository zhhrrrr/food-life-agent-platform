package com.foodlife.trade.trigger.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
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
    }

    private List<FlowRule> buildFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        rules.add(qpsRule(TradeSentinelResources.NORMAL_ORDER_CREATE, 60));
        rules.add(qpsRule(TradeSentinelResources.GROUP_BUY_ORDER_CREATE, 60));
        rules.add(qpsRule(TradeSentinelResources.SECKILL_ORDER_CREATE, 20));
        rules.add(qpsRule(TradeSentinelResources.SECKILL_ORDER_ASYNC_CREATE, 40));
        rules.add(qpsRule(TradeSentinelResources.PAYMENT_CALLBACK, 100));
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
        return rules;
    }
}
