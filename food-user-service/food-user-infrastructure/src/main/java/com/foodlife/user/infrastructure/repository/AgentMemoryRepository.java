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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

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
    public SessionContext findPendingSummary(Long userId, String sessionId) {
        AgentSessionPO session = mapper.findSession(userId, sessionId);
        if (session == null) return new SessionContext(sessionId, null, 0, 0, List.of());
        List<AgentMessagePO> messages = new ArrayList<>(mapper.findPendingMessages(userId, sessionId, value(session.getSummaryThroughSeq())));
        Collections.reverse(messages);
        return toContext(session, messages);
    }

    @Override
    public SessionContext appendTurn(Long userId, String sessionId, String turnId, String user,
                                     String assistant, String intent, String runId, String contextJson, int recentTurnLimit) {
        validateEntityContext(contextJson);
        mapper.ensureSession(userId, sessionId);
        AgentSessionPO session = mapper.lockSession(userId, sessionId);
        if (mapper.countTurn(userId, sessionId, turnId) == 0) {
            long firstSequence = session.getVersion() * 2 + 1;
            mapper.insertMessage(message(userId, sessionId, turnId, firstSequence, "user", user, intent, runId));
            AgentMessagePO answer = message(userId, sessionId, turnId, firstSequence + 1, "assistant", assistant, intent, runId);
            answer.setContextJson(contextJson);
            mapper.insertMessage(answer);
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
        validateProfile(memory);
        AgentUserMemoryPO po = new AgentUserMemoryPO();
        po.setUserId(userId); po.setMemoryType(memory.memoryType()); po.setMemoryKey(memory.memoryKey());
        mapper.ensureUserMemory(po);
        AgentUserMemoryPO old = mapper.lockUserMemory(userId, memory.memoryType(), memory.memoryKey());
        if (memory.sourceTurnId() != null && memory.sourceTurnId().equals(old.getSourceTurnId())) return;
        if (memory.version() >= 0 && memory.version() != value(old.getVersion()))
            throw new org.springframework.dao.OptimisticLockingFailureException("memory version conflict");
        po.setValueJson(mergeValue(old.getValueJson(), memory)); po.setSourceTurnId(memory.sourceTurnId());
        mapper.upsertUserMemory(po);
        // Retire old random-ID preference events when a current profile value supersedes them.
        mapper.findPreferenceEvents(userId, memory.memoryKey()).stream()
                .filter(e -> !e.getEventId().equals(profileEventId(userId, memory.memoryType(), memory.memoryKey())))
                .forEach(e -> deleteSemanticEvent(userId, e.getEventId()));
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        saveSemanticEvent(new SemanticMemoryEvent(profileEventId(userId, memory.memoryType(), memory.memoryKey()), userId,
                "explicit_preference", describeProfile(memory.memoryKey(), po.getValueJson()), "preference", memory.memoryKey(),
                memory.sourceTurnId(), "ACTIVE", now, now.plusDays(180), 0));
    }

    @Override
    public void deleteUserMemory(Long userId, String memoryType, String memoryKey) {
        mapper.deleteUserMemory(userId, memoryType, memoryKey);
        mapper.findPreferenceEvents(userId, memoryKey).forEach(event -> deleteSemanticEvent(userId, event.getEventId()));
    }

    @Override
    public void saveSemanticEvent(SemanticMemoryEvent event) {
        AgentMemoryEventPO old = mapper.lockEvent(event.eventId());
        if (old != null && !old.getUserId().equals(event.userId())) throw new IllegalArgumentException("memory event owner mismatch");
        if (old != null && event.sourceTurnId() != null && event.sourceTurnId().equals(old.getSourceTurnId())
                && event.content().equals(old.getContent()) && "ACTIVE".equals(old.getStatus())) return;
        if (old != null && event.version() > 0 && event.version() != value(old.getVersion()))
            throw new org.springframework.dao.OptimisticLockingFailureException("event version conflict");
        AgentMemoryEventPO po = event(event);
        if (old == null) mapper.upsertEvent(po);
        else { po.setVersion(old.getVersion()); if (mapper.updateEvent(po) == 0) throw new org.springframework.dao.OptimisticLockingFailureException("event version conflict"); }
        po = mapper.findEvent(event.userId(), event.eventId());
        enqueue("UPSERT", po);
    }

    @Override
    public void deleteSemanticEvent(Long userId, String eventId) {
        if (mapper.deleteEvent(userId, eventId) > 0) enqueue("DELETE", mapper.findEvent(userId, eventId));
    }

    @Override
    public SemanticMemoryEvent findSemanticEvent(Long userId, String eventId) {
        AgentMemoryEventPO po = mapper.findEvent(userId, eventId);
        return po == null ? null : model(po);
    }

    @Override
    public List<SemanticMemoryEvent> searchSemanticEvents(Long userId, String query, List<String> eventIds, int limit) {
        if (eventIds != null && eventIds.isEmpty()) return List.of();
        return mapper.searchEvents(userId, query, eventIds, limit).stream().map(this::model).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void expire(AgentMemoryEventPO event) {
        if (mapper.expireEvent(event.getId(), event.getVersion()) > 0)
            enqueue("DELETE", mapper.findEvent(event.getUserId(), event.getEventId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void replay(AgentMemoryEventPO event) { enqueue("ACTIVE".equals(event.getStatus()) ? "UPSERT" : "DELETE", event); }

    private SemanticMemoryEvent model(AgentMemoryEventPO po) {
        return new SemanticMemoryEvent(po.getEventId(), po.getUserId(), po.getEventType(), po.getContent(),
                po.getEntityType(), po.getEntityId(), po.getSourceTurnId(), po.getStatus(), po.getOccurredAt(),
                po.getExpiresAt(), value(po.getVersion()));
    }

    private String profileEventId(Long userId, String type, String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((userId + ":" + type + ":" + key).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private JsonNode profileJson(String value) {
        try { JsonNode node = objectMapper.readTree(value); if (node == null || !node.isObject()) throw new IllegalArgumentException("memory JSON object required"); return node; }
        catch (java.io.IOException e) { throw new IllegalArgumentException("invalid memory JSON", e); }
    }

    private void validateProfile(UserMemory memory) {
        if (!"food_preference".equals(memory.memoryType()) || !Set.of("favorite", "avoid", "allergy", "budget").contains(memory.memoryKey()))
            throw new IllegalArgumentException("unsupported preference field");
        JsonNode node = profileJson(memory.valueJson());
        if ("budget".equals(memory.memoryKey())) {
            if (!node.path("max_yuan").canConvertToInt() || node.path("max_yuan").asInt() < 0 || node.path("max_yuan").asInt() > 100000
                    || !Set.of("per_person", "total", "package", "unspecified").contains(node.path("budget_type").asText()))
                throw new IllegalArgumentException("invalid budget");
        } else {
            JsonNode values = node.get("values");
            if (values == null || !values.isArray() || values.isEmpty() || values.size() > 20) throw new IllegalArgumentException("preference values required");
            values.forEach(v -> { if (!v.isTextual() || v.asText().isBlank() || v.asText().length() > 40) throw new IllegalArgumentException("invalid preference value"); });
        }
        if (!Set.of("add", "replace", "remove").contains(node.path("operation").asText("replace"))) throw new IllegalArgumentException("invalid preference operation");
    }

    private String mergeValue(String oldValue, UserMemory memory) {
        JsonNode incoming = profileJson(memory.valueJson());
        ObjectNode result = objectMapper.createObjectNode();
        if ("budget".equals(memory.memoryKey())) {
            result.set("max_yuan", incoming.get("max_yuan")); result.set("budget_type", incoming.get("budget_type"));
        } else {
            Set<String> values = new LinkedHashSet<>();
            String operation = incoming.path("operation").asText("replace");
            if (!"replace".equals(operation)) {
                JsonNode old = profileJson(oldValue);
                old.path("values").forEach(v -> values.add(v.asText()));
                if (old.hasNonNull("value")) values.add(old.path("value").asText());
            }
            incoming.path("values").forEach(v -> { if ("remove".equals(operation)) values.remove(v.asText()); else values.add(v.asText()); });
            if (values.size() > 20) throw new IllegalArgumentException("too many preference values");
            result.set("values", objectMapper.valueToTree(values));
        }
        return result.toString();
    }

    private String describeProfile(String key, String value) {
        JsonNode node = profileJson(value);
        if ("budget".equals(key)) return "用户长期预算（" + switch(node.path("budget_type").asText()) {
            case "per_person" -> "人均"; case "total" -> "总额"; case "package" -> "套餐"; default -> "范围未指定";
        } + "）：" + node.path("max_yuan").asInt() + "元";
        List<String> values = new ArrayList<>(); node.path("values").forEach(v -> values.add(v.asText()));
        return switch(key) { case "allergy" -> "用户明确过敏限制："; case "avoid" -> "用户明确忌口："; default -> "用户明确饮食偏好："; }
                + String.join("、", values);
    }

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
                        item.getIntent(), item.getRunId(), item.getCreateTime(), item.getContextJson()));
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

    private void validateEntityContext(String value) {
        if (value == null) return; // Turns from older clients have no structured references.
        if (value.length() > 64000) throw new IllegalArgumentException("memory entity context too large");
        try {
            JsonNode root = objectMapper.readTree(value);
            JsonNode groups = root.path("entity_groups");
            if (!root.isObject() || !groups.isArray() || groups.size() > 12)
                throw new IllegalArgumentException("invalid entity groups");
            for (JsonNode group : groups) {
                String type = group.path("entity_type").asText();
                JsonNode candidates = group.path("candidates");
                if (!Set.of("shop", "package", "order", "team").contains(type)
                        || group.path("source").asText().length() > 128
                        || !candidates.isArray() || candidates.size() > 20)
                    throw new IllegalArgumentException("invalid entity group");
                for (JsonNode candidate : candidates) {
                    String id = candidate.path("id").asText();
                    if (!id.matches(type.equals("team") ? "[A-Za-z0-9_-]{1,64}" : "[1-9][0-9]{0,18}")
                            || candidate.path("label").asText().length() > 160)
                        throw new IllegalArgumentException("invalid entity candidate");
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("invalid entity context JSON", e);
        }
    }

    private AgentMemoryEventPO event(SemanticMemoryEvent source) {
        AgentMemoryEventPO po = new AgentMemoryEventPO(); po.setEventId(source.eventId()); po.setUserId(source.userId());
        po.setEventType(source.eventType()); po.setContent(source.content()); po.setEntityType(source.entityType());
        po.setEntityId(source.entityId()); po.setSourceTurnId(source.sourceTurnId()); po.setStatus(source.status());
        po.setOccurredAt(source.occurredAt()); po.setExpiresAt(source.expiresAt()); return po;
    }

    private long value(Long value) { return value == null ? 0 : value; }
}
