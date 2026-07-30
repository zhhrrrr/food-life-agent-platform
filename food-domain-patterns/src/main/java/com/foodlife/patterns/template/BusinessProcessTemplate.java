package com.foodlife.patterns.template;

public abstract class BusinessProcessTemplate<C, R> {

    public final R execute(C context) {
        beforeProcess(context);
        validate(context);
        R result = doProcess(context);
        afterProcess(context, result);
        return result;
    }

    protected void beforeProcess(C context) {
    }

    protected void validate(C context) {
    }

    protected abstract R doProcess(C context);

    protected void afterProcess(C context, R result) {
    }
}
