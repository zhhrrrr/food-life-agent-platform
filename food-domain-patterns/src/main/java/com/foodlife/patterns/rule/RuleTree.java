package com.foodlife.patterns.rule;

import java.util.Map;

public interface RuleTree<C, R> {

    String group();

    String root();

    Map<String, RuleNode<C, R>> nodes();
}
