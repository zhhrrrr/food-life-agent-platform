package com.foodlife.user.types.constants;

public final class UserRedisConstants {

    public static final String LOGIN_CODE_KEY = "food:login:code:";
    public static final long LOGIN_CODE_TTL_MINUTES = 2L;
    public static final String LOGIN_TOKEN_KEY = "food:login:token:";
    public static final long LOGIN_TOKEN_TTL_MINUTES = 30L;

    public static final String USER_PHONE_KEY = "food:user:phone:";
    public static final String USER_DATA_KEY = "food:user:data:";
    public static final String USER_ID_SEQUENCE_KEY = "food:user:id:seq";
    public static final String USER_NICK_NAME_PREFIX = "food_user_";

    private UserRedisConstants() {
    }
}
