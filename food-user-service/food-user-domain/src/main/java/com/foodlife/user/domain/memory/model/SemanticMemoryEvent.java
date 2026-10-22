package com.foodlife.user.domain.memory.model;

import java.time.LocalDateTime;

public record SemanticMemoryEvent(String eventId, Long userId, String eventType,
                                  String content, String entityType, String entityId,
                                  String sourceTurnId, String status,
                                  LocalDateTime occurredAt, LocalDateTime expiresAt, long version) {
}
