package com.foodlife.user.infrastructure.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.user.domain.memory.model.SemanticMemoryEvent;
import com.foodlife.user.domain.memory.model.SessionContext;
import com.foodlife.user.domain.memory.model.SessionTurn;
import com.foodlife.user.domain.memory.model.UserMemory;
import com.foodlife.user.domain.memory.repository.IAgentMemoryRepository;
import com.foodlife.user.infrastructure.dao.IAgentMemoryMapper;
import com.foodlife.user.infrastructure.dao.po.*;
import com.foodlife.user.infrastructure.mq.AgentMemoryOutboxPublisher;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;

@Repository
public class AgentMemoryRepository implements IAgentMemoryRepository {
    private final IAgentMemoryMapper mapper;
    private final ObjectMapper objectMapper;
    private final AgentMemoryOutboxPublisher publisher;

    public AgentMemoryRepository(IAgentMemoryMapper mapper, ObjectMapper objectMapper,
                                 AgentMemoryOutboxPublisher publisher) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
    }

    @Override
    public SessionContext findSession(Long userId, String sessionId, int recentTurnLimit) {
        AgentSessionPO session = mapper.findSession(userId, sessionId);
        if (session == null) return new SessionContext(sessionId, null, 0, 0, List.of());
        return toContext(session, mapper.findRecentMessages(userId, sessionId, recentTurnLimit * 2));
    }

    @Override
    public SessionContext appendTurn(Long userId, String sessionId, String turnId, String user,
                                     String assistant, String intent, String runId, int recentTurnLimit) {
        mapper.ensureSession(userId, sessionId);
        AgentSessionPO session = mapper.lockSession(userId, sessionId);
        if (mapper.countTurn(userId, sessionId, turnId) == 0) {
            long firstSequence = session.getVersion() * 2 + 1;
            mapper.insertMessage(message(userId, sessionId, turnId, firstSequence, "user", user, intent, runId));
            mapper.insertMessage(message(userId, sessionId, turnId, firstSequence + 1, "assistant", assistant, intent, runId));
            mapper.bumpSessionVersion(userId, sessionId);
        }
        return findSession(userId, sessionId, recentTurnLimit);
    }

    @Override
    public boolean updateSummary(Long userId, String sessionId, String summary,
                                 long summaryThroughSeq, long expectedVersion) {
        return mapper.updateSummary(userId, sessionId, summary, summaryThroughSeq, expectedVersion) > 0;
    }

    @Override
    public List<UserMemory> findUserMemories(Long userId) {
        return mapper.findUserMemories(userId).stream()
                .map(item -> new UserMemory(item.getMemoryType(), item.getMemoryKey(), item.getValueJson(),
                        item.getSourceTurnId(), value(item.getVersion())))
                .toList();
    }

    @Override
    public void upsertUserMemory(Long userId, UserMemory memory) {
        AgentUserMemoryPO po = new AgentUserMemoryPO();
        po.setUserId(userId); po.setMemoryType(memory.memoryType()); po.setMemoryKey(memory.memoryKey());
        po.setValueJson(memory.valueJson()); po.setSourceTurnId(memory.sourceTurnId());
        mapper.upsertUserMemory(po);
    }

    @Override
    public void deleteUserMemory(Long userId, String memoryType, String memoryKey) {
        mapper.deleteUserMemory(userId, memoryType, memoryKey);
    }

    @Override
    public void saveSemanticEvent(SemanticMemoryEvent event) {
        AgentMemoryEventPO po = event(event);
        mapper.upsertEvent(po);
        enqueue("UPSERT", po);
    }

    @Override
    public void deleteSemanticEvent(Long userId, String eventId) {
        AgentMemoryEventPO po = mapper.findEvent(userId, eventId);
        if (po != null && mapper.deleteEvent(userId, eventId) > 0) enqueue("DELETE", po);
    }

    public void expire(AgentMemoryEventPO event) {
        if (mapper.expireEvent(event.getId()) > 0) enqueue("DELETE", event);
    }

    public void replay(AgentMemoryEventPO event) { enqueue("UPSERT", event); }

    private void enqueue(String eventType, AgentMemoryEventPO event) {
        try {
            String messageId = UUID.randomUUID().toString().replace("-", "");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messageId", messageId); body.put("eventType", eventType);
            body.put("eventId", event.getEventId()); body.put("payload", event);
            UserMemoryLocalMessagePO outbox = new UserMemoryLocalMessagePO();
            outbox.setMessageId(messageId); outbox.setEventType(eventType); outbox.setEventId(event.getEventId());
            outbox.setContent(objectMapper.writeValueAsString(body));
            mapper.insertOutbox(outbox);
            publisher.publishAfterCommit(messageId);
        } catch (Exception exception) {
            throw new IllegalStateException("build agent memory outbox failed", exception);
        }
    }

    private SessionContext toContext(AgentSessionPO session, List<AgentMessagePO> messages) {
        List<AgentMessagePO> ordered = new ArrayList<>(messages); Collections.reverse(ordered);
        Map<String, AgentMessagePO> users = new LinkedHashMap<>();
        List<SessionTurn> turns = new ArrayList<>();
        for (AgentMessagePO item : ordered) {
            if ("user".equals(item.getRole())) users.put(item.getTurnId(), item);
            if ("assistant".equals(item.getRole()) && users.containsKey(item.getTurnId())) {
                AgentMessagePO user = users.remove(item.getTurnId());
                turns.add(new SessionTurn(item.getTurnId(), item.getSequenceNo(), user.getContent(), item.getContent(),
                        item.getIntent(), item.getRunId(), item.getCreateTime()));
            }
        }
        return new SessionContext(session.getSessionId(), session.getSummary(), value(session.getSummaryThroughSeq()),
                value(session.getVersion()), turns);
    }

    private AgentMessagePO message(Long userId, String sessionId, String turnId, long sequence,
                                   String role, String content, String intent, String runId) {
        AgentMessagePO po = new AgentMessagePO(); po.setUserId(userId); po.setSessionId(sessionId);
        po.setTurnId(turnId); po.setSequenceNo(sequence); po.setRole(role); po.setContent(content);
        po.setIntent(intent); po.setRunId(runId); return po;
    }

    private AgentMemoryEventPO event(SemanticMemoryEvent source) {
        AgentMemoryEventPO po = new AgentMemoryEventPO(); po.setEventId(source.eventId()); po.setUserId(source.userId());
        po.setEventType(source.eventType()); po.setContent(source.content()); po.setEntityType(source.entityType());
        po.setEntityId(source.entityId()); po.setSourceTurnId(source.sourceTurnId()); po.setStatus(source.status());
        po.setOccurredAt(source.occurredAt()); po.setExpiresAt(source.expiresAt()); return po;
    }

    private long value(Long value) { return value == null ? 0 : value; }
}
