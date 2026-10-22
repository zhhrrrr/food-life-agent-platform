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
    private static final int MAX_RECENT_TURNS = 50;
    private final IAgentMemoryRepository repository;

    public AgentMemoryDomainService(IAgentMemoryRepository repository) {
        this.repository = repository;
    }

    public SessionContext loadSession(Long userId, String sessionId, int limit) {
        requireUser(userId);
        return repository.findSession(userId, requireText(sessionId, "sessionId"), normalizeLimit(limit));
    }

    public SessionContext loadPendingSummary(Long userId, String sessionId) {
        requireUser(userId);
        return repository.findPendingSummary(userId, requireText(sessionId, "sessionId"));
    }

    @Transactional(rollbackFor = Exception.class)
    public SessionContext appendTurn(Long userId, String sessionId, String turnId, String user,
                                     String assistant, String intent, String runId, String contextJson) {
        requireUser(userId);
        if (!requireText(sessionId, "sessionId").matches("[A-Za-z0-9_-]{1,64}") || !requireText(turnId, "turnId").matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("invalid session or turn ID");
        if (requireText(user, "user").codePointCount(0,user.length()) > 4000 || requireText(assistant,"assistant").codePointCount(0,assistant.length()) > 4000
                || (intent != null && intent.length() > 32) || (runId != null && runId.length() > 64))
            throw new IllegalArgumentException("memory turn too large");
        if (contextJson != null && contextJson.length() > 64000)
            throw new IllegalArgumentException("memory entity context too large");
        return repository.appendTurn(userId, sessionId, turnId, user, assistant, trim(intent), trim(runId), contextJson, MAX_RECENT_TURNS);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateSummary(Long userId, String sessionId, String summary,
                                 long summaryThroughSeq, long expectedVersion) {
        requireUser(userId);
        if (summaryThroughSeq < 0 || expectedVersion < 0) {
            throw new IllegalArgumentException("summary sequence and version must be non-negative");
        }
        SessionContext context = repository.findSession(userId, sessionId, 1);
        long lastSequence = context.turns().isEmpty() ? 0 : context.turns().get(context.turns().size()-1).sequenceNo();
        if (summaryThroughSeq > lastSequence || summary == null || summary.length() > 2000)
            throw new IllegalArgumentException("invalid summary coverage or size");
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
                ? LocalDateTime.now(java.time.Clock.systemUTC()).plusDays(180) : event.expiresAt();
        if (event.eventId() == null || !event.eventId().matches("[A-Za-z0-9_-]{1,64}") || event.content() == null || event.content().length() > 1000
                || !expiresAt.isAfter(LocalDateTime.now(java.time.Clock.systemUTC()))) throw new IllegalArgumentException("invalid memory event");
        repository.saveSemanticEvent(new SemanticMemoryEvent(
                requireText(event.eventId(), "eventId"), userId,
                requireText(event.eventType(), "eventType"), requireText(event.content(), "content"),
                trim(event.entityType()), trim(event.entityId()), trim(event.sourceTurnId()),
                "ACTIVE", event.occurredAt() == null ? LocalDateTime.now(java.time.Clock.systemUTC()) : event.occurredAt(), expiresAt, event.version()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSemanticEvent(Long userId, String eventId) {
        requireUser(userId);
        repository.deleteSemanticEvent(userId, requireText(eventId, "eventId"));
    }

    public SemanticMemoryEvent findSemanticEvent(Long userId, String eventId) {
        requireUser(userId);
        return repository.findSemanticEvent(userId, requireText(eventId, "eventId"));
    }

    public List<SemanticMemoryEvent> searchSemanticEvents(Long userId, String query, List<String> eventIds, int limit) {
        requireUser(userId);
        if (eventIds != null && eventIds.size() > 50) throw new IllegalArgumentException("too many memory IDs");
        return repository.searchSemanticEvents(userId, query == null ? "" : query, eventIds, Math.max(1, Math.min(limit, 50)));
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
