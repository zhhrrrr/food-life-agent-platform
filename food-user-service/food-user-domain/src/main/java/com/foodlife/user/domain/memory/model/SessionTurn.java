package com.foodlife.user.domain.memory.model;

import java.time.LocalDateTime;

public record SessionTurn(String turnId, long sequenceNo, String user, String assistant,
                          String intent, String runId, LocalDateTime createTime, String contextJson) {
}
