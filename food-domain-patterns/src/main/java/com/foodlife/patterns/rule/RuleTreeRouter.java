package com.foodlife.patterns.rule;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RuleTreeRouter {

    private static final int MAX_DEPTH = 64;

    private final List<RuleTree<?, ?>> ruleTrees;

    public RuleTreeRouter(List<RuleTree<?, ?>> ruleTrees) {
        this.ruleTrees = ruleTrees;
    }

    @SuppressWarnings("unchecked")
    public <C, R> R execute(String group, C context) {
        for (RuleTree<?, ?> ruleTree : ruleTrees) {
            RuleTree<C, R> typedRuleTree = (RuleTree<C, R>) ruleTree;
            if (group.equals(typedRuleTree.group())) {
                return executeTree(typedRuleTree, context);
            }
        }
        throw new IllegalArgumentException("rule tree not found");
    }

    private <C, R> R executeTree(RuleTree<C, R> ruleTree, C context) {
        Map<String, RuleNode<C, R>> nodes = ruleTree.nodes();
        String nodeKey = ruleTree.root();
        R result = null;
        int depth = 0;
        while (nodeKey != null) {
            if (depth++ > MAX_DEPTH) {
                throw new IllegalStateException("rule tree depth overflow");
            }
            RuleNode<C, R> node = nodes.get(nodeKey);
            if (node == null) {
                throw new IllegalArgumentException("rule node not found: " + nodeKey);
            }
            result = node.apply(context);
            nodeKey = node.next(context, result);
        }
        return result;
    }
}
