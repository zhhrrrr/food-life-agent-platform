package com.foodlife.user.trigger.http;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.user.domain.memory.model.SemanticMemoryEvent;
import com.foodlife.user.domain.memory.model.SessionContext;
import com.foodlife.user.domain.memory.model.UserMemory;
import com.foodlife.user.domain.memory.service.AgentMemoryDomainService;
import com.foodlife.user.types.response.Response;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/user/agent-memory")
public class AgentMemoryController {
    private final AgentMemoryDomainService service;

    public AgentMemoryController(AgentMemoryDomainService service) { this.service = service; }

    @GetMapping("/sessions/{sessionId}")
    public Response<SessionContext> loadSession(@PathVariable String sessionId,
                                                @RequestParam(defaultValue = "8") int limit) {
        return Response.success(service.loadSession(UserHolder.getUserId(), sessionId, limit));
    }

    @PostMapping("/sessions/{sessionId}/turns")
    public Response<SessionContext> appendTurn(@PathVariable String sessionId,
                                               @RequestBody AppendTurnRequest request) {
        return Response.success(service.appendTurn(UserHolder.getUserId(), sessionId, request.turnId(),
                request.user(), request.assistant(), request.intent(), request.runId()));
    }

    @PutMapping("/sessions/{sessionId}/summary")
    public Response<SummaryUpdateResponse> updateSummary(@PathVariable String sessionId,
                                                         @RequestBody SummaryUpdateRequest request) {
        boolean updated = service.updateSummary(UserHolder.getUserId(), sessionId, request.summary(),
                request.summaryThroughSeq(), request.expectedVersion());
        return Response.success(new SummaryUpdateResponse(updated));
    }

    @GetMapping("/profile")
    public Response<List<UserMemory>> loadProfile() {
        return Response.success(service.loadUserMemories(UserHolder.getUserId()));
    }

    @PutMapping("/profile/{memoryType}/{memoryKey}")
    public Response<Boolean> upsertProfile(@PathVariable String memoryType, @PathVariable String memoryKey,
                                           @RequestBody UserMemoryRequest request) {
        service.upsertUserMemory(UserHolder.getUserId(), new UserMemory(memoryType, memoryKey,
                request.valueJson(), request.sourceTurnId(), 0));
        return Response.success(true);
    }

    @DeleteMapping("/profile/{memoryType}/{memoryKey}")
    public Response<Boolean> deleteProfile(@PathVariable String memoryType, @PathVariable String memoryKey) {
        service.deleteUserMemory(UserHolder.getUserId(), memoryType, memoryKey);
        return Response.success(true);
    }

    @PostMapping("/events")
    public Response<Boolean> saveEvent(@RequestBody SemanticEventRequest request) {
        Long userId = UserHolder.getUserId();
        service.saveSemanticEvent(userId, new SemanticMemoryEvent(request.eventId(), userId,
                request.eventType(), request.content(), request.entityType(), request.entityId(),
                request.sourceTurnId(), "ACTIVE", request.occurredAt(), request.expiresAt()));
        return Response.success(true);
    }

    @DeleteMapping("/events/{eventId}")
    public Response<Boolean> deleteEvent(@PathVariable String eventId) {
        service.deleteSemanticEvent(UserHolder.getUserId(), eventId);
        return Response.success(true);
    }

    public record AppendTurnRequest(String turnId, String user, String assistant, String intent, String runId) {}
    public record SummaryUpdateRequest(String summary, long summaryThroughSeq, long expectedVersion) {}
    public record SummaryUpdateResponse(boolean updated) {}
    public record UserMemoryRequest(String valueJson, String sourceTurnId) {}
    public record SemanticEventRequest(String eventId, String eventType, String content,
                                       String entityType, String entityId, String sourceTurnId,
                                       LocalDateTime occurredAt, LocalDateTime expiresAt) {}
}
