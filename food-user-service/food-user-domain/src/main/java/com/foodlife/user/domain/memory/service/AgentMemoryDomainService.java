package com.foodlife.user.domain.memory.service;

import com.foodlife.user.domain.memory.model.SemanticMemoryEvent;
import com.foodlife.user.domain.memory.model.SessionContext;
import com.foodlife.user.domain.memory.model.UserMemory;
import com.foodlife.user.domain.memory.repository.IAgentMemoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AgentMemoryDomainService {
    private static final int MAX_RECENT_TURNS = 20;
    private final IAgentMemoryRepository repository;

    public AgentMemoryDomainService(IAgentMemoryRepository repository) {
        this.repository = repository;
    }

    public SessionContext loadSession(Long userId, String sessionId, int limit) {
        requireUser(userId);
        return repository.findSession(userId, requireText(sessionId, "sessionId"), normalizeLimit(limit));
    }

    @Transactional(rollbackFor = Exception.class)
    public SessionContext appendTurn(Long userId, String sessionId, String turnId, String user,
                                     String assistant, String intent, String runId) {
        requireUser(userId);
        return repository.appendTurn(userId, requireText(sessionId, "sessionId"),
                requireText(turnId, "turnId"), requireText(user, "user"),
                requireText(assistant, "assistant"), trim(intent), trim(runId), MAX_RECENT_TURNS);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateSummary(Long userId, String sessionId, String summary,
                                 long summaryThroughSeq, long expectedVersion) {
        requireUser(userId);
        if (summaryThroughSeq < 0 || expectedVersion < 0) {
            throw new IllegalArgumentException("summary sequence and version must be non-negative");
        }
        return repository.updateSummary(userId, requireText(sessionId, "sessionId"),
                requireText(summary, "summary"), summaryThroughSeq, expectedVersion);
    }

    public List<UserMemory> loadUserMemories(Long userId) {
        requireUser(userId);
        return repository.findUserMemories(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void upsertUserMemory(Long userId, UserMemory memory) {
        requireUser(userId);
        if (memory == null) throw new IllegalArgumentException("memory required");
        repository.upsertUserMemory(userId, new UserMemory(
                requireText(memory.memoryType(), "memoryType"),
                requireText(memory.memoryKey(), "memoryKey"),
                requireText(memory.valueJson(), "valueJson"), trim(memory.sourceTurnId()), memory.version()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteUserMemory(Long userId, String memoryType, String memoryKey) {
        requireUser(userId);
        repository.deleteUserMemory(userId, requireText(memoryType, "memoryType"),
                requireText(memoryKey, "memoryKey"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveSemanticEvent(Long userId, SemanticMemoryEvent event) {
        requireUser(userId);
        if (event == null || !userId.equals(event.userId())) {
            throw new IllegalArgumentException("event user mismatch");
        }
        LocalDateTime expiresAt = event.expiresAt() == null
                ? LocalDateTime.now().plusDays(180) : event.expiresAt();
        repository.saveSemanticEvent(new SemanticMemoryEvent(
                requireText(event.eventId(), "eventId"), userId,
                requireText(event.eventType(), "eventType"), requireText(event.content(), "content"),
                trim(event.entityType()), trim(event.entityId()), trim(event.sourceTurnId()),
                "ACTIVE", event.occurredAt() == null ? LocalDateTime.now() : event.occurredAt(), expiresAt));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSemanticEvent(Long userId, String eventId) {
        requireUser(userId);
        repository.deleteSemanticEvent(userId, requireText(eventId, "eventId"));
    }

    private int normalizeLimit(int limit) { return Math.max(1, Math.min(limit, MAX_RECENT_TURNS)); }
    private void requireUser(Long userId) { if (userId == null) throw new IllegalArgumentException("user not login"); }
    private String requireText(String value, String name) {
        String normalized = trim(value);
        if (normalized == null) throw new IllegalArgumentException(name + " required");
        return normalized;
    }
    private String trim(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }
}
