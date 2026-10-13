package com.foodlife.trade.domain.order.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Stable identities across response loss, transactions and process restarts. */
public final class TradeRequestIdentity {
    private TradeRequestIdentity() { }

    public static void validate(Long userId, String requestId) {
        if (userId == null || requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("invalid trade requestId or user");
        }
    }

    public static String fingerprint(Object... values) {
        StringBuilder input = new StringBuilder();
        for (Object value : values) {
            String text = value == null ? "" : value.toString();
            input.append(value == null ? -1 : text.length()).append(':').append(text);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String seckillRequestNo(Long userId, String requestId) {
        validate(userId, requestId);
        return "SKR" + fingerprint("SECKILL", userId, requestId).substring(0, 48);
    }
}
