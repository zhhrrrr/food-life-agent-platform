package com.foodlife.auth.constants;

public final class AuthConstants {

    public static final String DEFAULT_TOKEN_HEADER = "authorization";
    public static final String DEFAULT_TOKEN_PREFIX = "food:login:token:";
    public static final long DEFAULT_TOKEN_TTL_MINUTES = 30L;

    private AuthConstants() {
    }
}
