package com.foodlife.user.domain.memory.repository;

import com.foodlife.user.domain.memory.model.SemanticMemoryEvent;
import com.foodlife.user.domain.memory.model.SessionContext;
import com.foodlife.user.domain.memory.model.UserMemory;

import java.util.List;

public interface IAgentMemoryRepository {
    SessionContext findSession(Long userId, String sessionId, int recentTurnLimit);
    SessionContext findPendingSummary(Long userId, String sessionId);

    default SessionContext appendTurn(Long userId, String sessionId, String turnId, String user,
                              String assistant, String intent, String runId, int recentTurnLimit) {
        return appendTurn(userId, sessionId, turnId, user, assistant, intent, runId, null, recentTurnLimit);
    }
    SessionContext appendTurn(Long userId, String sessionId, String turnId, String user,
                              String assistant, String intent, String runId, String contextJson, int recentTurnLimit);

    boolean updateSummary(Long userId, String sessionId, String summary,
                          long summaryThroughSeq, long expectedVersion);

    List<UserMemory> findUserMemories(Long userId);

    void upsertUserMemory(Long userId, UserMemory memory);

    void deleteUserMemory(Long userId, String memoryType, String memoryKey);

    void saveSemanticEvent(SemanticMemoryEvent event);

    void deleteSemanticEvent(Long userId, String eventId);
    SemanticMemoryEvent findSemanticEvent(Long userId, String eventId);
    List<SemanticMemoryEvent> searchSemanticEvents(Long userId, String query, List<String> eventIds, int limit);
}
