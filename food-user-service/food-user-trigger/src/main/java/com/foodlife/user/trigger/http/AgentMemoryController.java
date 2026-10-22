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
@RequestMapping({"/api/user/agent-memory", "/api/internal/agent-memory/{owner}"})
public class AgentMemoryController {
    private final AgentMemoryDomainService service;
    private final com.foodlife.auth.properties.AuthProperties auth;

    public AgentMemoryController(AgentMemoryDomainService service, com.foodlife.auth.properties.AuthProperties auth) { this.service = service; this.auth = auth; }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public Response<Void> invalidMemory(IllegalArgumentException exception) {
        return Response.fail("INVALID_MEMORY", exception.getMessage());
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.CONFLICT)
    public Response<Void> conflictingMemory(org.springframework.dao.OptimisticLockingFailureException exception) {
        return Response.fail("MEMORY_CONFLICT", exception.getMessage());
    }

    @GetMapping("/sessions/{sessionId}")
    public Response<SessionContext> loadSession(@PathVariable String sessionId, @PathVariable(required=false) Long owner,
                                                @RequestParam(defaultValue = "8") int limit) {
        return Response.success(service.loadSession(user(owner), sessionId, limit));
    }

    @PostMapping("/sessions/{sessionId}/turns")
    public Response<SessionContext> appendTurn(@PathVariable String sessionId, @PathVariable(required=false) Long owner,
                                               @RequestBody AppendTurnRequest request) {
        return Response.success(service.appendTurn(user(owner), sessionId, request.turnId(),
                request.user(), request.assistant(), request.intent(), request.runId(), request.contextJson()));
    }

    @GetMapping("/sessions/{sessionId}/pending")
    public Response<SessionContext> pending(@PathVariable String sessionId, @PathVariable(required=false) Long owner) {
        return Response.success(service.loadPendingSummary(user(owner), sessionId));
    }

    @PutMapping("/sessions/{sessionId}/summary")
    public Response<SummaryUpdateResponse> updateSummary(@PathVariable String sessionId, @PathVariable(required=false) Long owner,
                                                         @RequestBody SummaryUpdateRequest request) {
        boolean updated = service.updateSummary(user(owner), sessionId, request.summary(),
                request.summaryThroughSeq(), request.expectedVersion());
        return Response.success(new SummaryUpdateResponse(updated));
    }

    @GetMapping("/profile")
    public Response<List<UserMemory>> loadProfile(@PathVariable(required=false) Long owner) {
        return Response.success(service.loadUserMemories(user(owner)));
    }

    @PutMapping("/profile/{memoryType}/{memoryKey}")
    public Response<Boolean> upsertProfile(@PathVariable String memoryType, @PathVariable String memoryKey, @PathVariable(required=false) Long owner,
                                           @RequestBody UserMemoryRequest request) {
        service.upsertUserMemory(user(owner), new UserMemory(memoryType, memoryKey,
                request.valueJson(), request.sourceTurnId(), request.expectedVersion() == null ? -1 : request.expectedVersion()));
        return Response.success(true);
    }

    @DeleteMapping("/profile/{memoryType}/{memoryKey}")
    public Response<Boolean> deleteProfile(@PathVariable String memoryType, @PathVariable String memoryKey, @PathVariable(required=false) Long owner) {
        service.deleteUserMemory(user(owner), memoryType, memoryKey);
        return Response.success(true);
    }

    @PostMapping("/events")
    public Response<Boolean> saveEvent(@RequestBody SemanticEventRequest request, @PathVariable(required=false) Long owner) {
        Long userId = user(owner);
        service.saveSemanticEvent(userId, new SemanticMemoryEvent(request.eventId(), userId,
                request.eventType(), request.content(), request.entityType(), request.entityId(),
                request.sourceTurnId(), "ACTIVE", request.occurredAt(), request.expiresAt(), request.expectedVersion() == null ? 0 : request.expectedVersion()));
        return Response.success(true);
    }

    @DeleteMapping("/events/{eventId}")
    public Response<Boolean> deleteEvent(@PathVariable String eventId, @PathVariable(required=false) Long owner) {
        service.deleteSemanticEvent(user(owner), eventId);
        return Response.success(true);
    }

    @GetMapping("/events/{eventId}")
    public Response<SemanticMemoryEvent> event(@PathVariable String eventId, @PathVariable(required=false) Long owner) {
        return Response.success(service.findSemanticEvent(user(owner), eventId));
    }

    @PostMapping("/events/search")
    public Response<List<SemanticMemoryEvent>> events(@RequestBody EventSearch request, @PathVariable(required=false) Long owner) {
        return Response.success(service.searchSemanticEvents(user(owner), request.query(), request.eventIds(), request.limit()));
    }

    private Long user(Long owner) {
        if (owner == null) return UserHolder.getUserId();
        var attributes = (org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
        String supplied = attributes.getRequest().getHeader(auth.getInternalCall().getSecretHeaderName());
        String expected = auth.getInternalCall().getSecret();
        if (owner <= 0 || expected == null || expected.isBlank() || supplied == null ||
                !java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8), supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        return owner;
    }

    public record EventSearch(String query, List<String> eventIds, int limit) {}

    public record AppendTurnRequest(String turnId, String user, String assistant, String intent, String runId, String contextJson) {}
    public record SummaryUpdateRequest(String summary, long summaryThroughSeq, long expectedVersion) {}
    public record SummaryUpdateResponse(boolean updated) {}
    public record UserMemoryRequest(String valueJson, String sourceTurnId, Long expectedVersion) {}
    public record SemanticEventRequest(String eventId, String eventType, String content,
                                       String entityType, String entityId, String sourceTurnId,
                                       LocalDateTime occurredAt, LocalDateTime expiresAt, Long expectedVersion) {}
}
