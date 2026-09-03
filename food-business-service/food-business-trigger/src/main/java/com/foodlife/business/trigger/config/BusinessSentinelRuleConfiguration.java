package com.foodlife.business.trigger.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.foodlife.business.trigger.sentinel.BusinessSentinelResources;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class BusinessSentinelRuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void initRules() {
        ParamFlowRuleManager.loadRules(buildParamFlowRules());
        DegradeRuleManager.loadRules(buildDegradeRules());
    }

    private List<ParamFlowRule> buildParamFlowRules() {
        List<ParamFlowRule> rules = new ArrayList<>();
        rules.add(packageIdHotspotRule(BusinessSentinelResources.PACKAGE_STOCK_OCCUPY, 20));
        rules.add(packageIdHotspotRule(BusinessSentinelResources.PACKAGE_STOCK_RELEASE, 40));
        rules.add(packageIdHotspotRule(BusinessSentinelResources.PACKAGE_SOLD_CONFIRM, 40));
        rules.add(packageIdHotspotRule(BusinessSentinelResources.PACKAGE_SOLD_ROLLBACK, 40));
        return rules;
    }

    private ParamFlowRule packageIdHotspotRule(String resource, double count) {
        return new ParamFlowRule(resource)
                .setParamIdx(0)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(count)
                .setDurationInSec(1);
    }

    private List<DegradeRule> buildDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();
        rules.add(packageStockExceptionRatioRule(BusinessSentinelResources.PACKAGE_STOCK_OCCUPY));
        rules.add(packageStockExceptionRatioRule(BusinessSentinelResources.PACKAGE_STOCK_RELEASE));
        rules.add(packageStockExceptionRatioRule(BusinessSentinelResources.PACKAGE_SOLD_CONFIRM));
        rules.add(packageStockExceptionRatioRule(BusinessSentinelResources.PACKAGE_SOLD_ROLLBACK));
        return rules;
    }

    private DegradeRule packageStockExceptionRatioRule(String resource) {
        return new DegradeRule(resource)
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                .setCount(0.5)
                .setMinRequestAmount(5)
                .setStatIntervalMs(10000)
                .setTimeWindow(10);
    }
}
