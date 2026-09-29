package com.foodlife.user.domain.memory.model;

import java.util.List;

public record SessionContext(String sessionId, String summary, long summaryThroughSeq,
                             long version, List<SessionTurn> turns) {
}
