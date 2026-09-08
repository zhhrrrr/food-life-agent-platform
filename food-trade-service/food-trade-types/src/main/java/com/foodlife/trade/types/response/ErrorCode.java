package com.foodlife.trade.types.response;

public enum ErrorCode {

    SUCCESS("0000", "success"),
    BAD_REQUEST("400", "bad request"),
    UNAUTHORIZED("401", "unauthorized"),
    FORBIDDEN("403", "forbidden"),
    NOT_FOUND("404", "not found"),
    TOO_MANY_REQUESTS("429", "too many requests"),
    SERVICE_UNAVAILABLE("503", "service unavailable"),
    INTERNAL_ERROR("500", "internal server error");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
