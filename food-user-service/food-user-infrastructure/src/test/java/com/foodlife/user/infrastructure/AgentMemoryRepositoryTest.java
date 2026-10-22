package com.foodlife.user.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.user.domain.memory.model.SemanticMemoryEvent;
import com.foodlife.user.domain.memory.model.UserMemory;
import com.foodlife.user.infrastructure.dao.IAgentMemoryMapper;
import com.foodlife.user.infrastructure.dao.po.*;
import com.foodlife.user.infrastructure.mq.AgentMemoryOutboxPublisher;
import com.foodlife.user.infrastructure.repository.AgentMemoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentMemoryRepositoryTest {
    private final IAgentMemoryMapper mapper = mock(IAgentMemoryMapper.class);
    private final AgentMemoryRepository repository = new AgentMemoryRepository(mapper,
            new ObjectMapper().findAndRegisterModules(), mock(AgentMemoryOutboxPublisher.class));

    @Test void allergyUpdatesMergeAndPublishCanonicalEvent() throws Exception {
        AgentUserMemoryPO old = new AgentUserMemoryPO(); old.setVersion(1L);
        old.setValueJson("{\"values\":[\"花生\"]}"); old.setSourceTurnId("old-turn");
        when(mapper.lockUserMemory(7L, "food_preference", "allergy")).thenReturn(old);
        AtomicReference<AgentMemoryEventPO> saved = new AtomicReference<>();
        when(mapper.upsertEvent(any())).thenAnswer(a -> { AgentMemoryEventPO p=a.getArgument(0); p.setVersion(1L); saved.set(p); return 1; });
        when(mapper.findEvent(eq(7L), anyString())).thenAnswer(a -> saved.get());
        repository.upsertUserMemory(7L, new UserMemory("food_preference", "allergy",
                "{\"values\":[\"海鲜\"],\"operation\":\"add\"}", "new-turn", -1));
        ArgumentCaptor<AgentUserMemoryPO> profile = ArgumentCaptor.forClass(AgentUserMemoryPO.class);
        verify(mapper).upsertUserMemory(profile.capture());
        var values = new ObjectMapper().readTree(profile.getValue().getValueJson()).path("values");
        assertEquals(2, values.size()); assertEquals("花生", values.get(0).asText()); assertEquals("海鲜", values.get(1).asText());
        assertEquals(64, saved.get().getEventId().length());
        assertEquals("new-turn", saved.get().getSourceTurnId());
        verify(mapper).insertOutbox(any());
    }

    @Test void existingEventCannotBeOverwrittenByAnotherOwner() {
        AgentMemoryEventPO old = new AgentMemoryEventPO(); old.setUserId(8L);
        when(mapper.lockEvent("shared-id")).thenReturn(old);
        LocalDateTime now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class, () -> repository.saveSemanticEvent(new SemanticMemoryEvent(
                "shared-id", 7L, "experience", "new", "", "", "t", "ACTIVE", now, now.plusDays(1), 0)));
        verify(mapper, never()).upsertEvent(any()); verify(mapper, never()).updateEvent(any());
    }

    @Test void deletingProfileDeletesAssociatedEvents() {
        AgentMemoryEventPO event = new AgentMemoryEventPO(); event.setEventId("legacy"); event.setUserId(7L);
        event.setStatus("DELETED"); event.setVersion(2L);
        when(mapper.findPreferenceEvents(7L, "favorite")).thenReturn(List.of(event));
        when(mapper.deleteEvent(7L, "legacy")).thenReturn(1); when(mapper.findEvent(7L,"legacy")).thenReturn(event);
        repository.deleteUserMemory(7L, "food_preference", "favorite");
        verify(mapper).deleteEvent(7L, "legacy"); verify(mapper).insertOutbox(any());
    }

    @Test void unsupportedProfileIsRejectedBeforeWriting() {
        assertThrows(IllegalArgumentException.class, () -> repository.upsertUserMemory(7L,
                new UserMemory("system", "instructions", "{}", "turn", -1)));
        verify(mapper, never()).upsertUserMemory(any());
    }

    @Test void displayedCandidateOrderSurvivesMessagePersistence() {
        AgentSessionPO session = new AgentSessionPO(); session.setSessionId("s"); session.setVersion(1L);
        when(mapper.lockSession(7L, "s")).thenReturn(session);
        when(mapper.findSession(7L, "s")).thenReturn(session);
        String context = "{\"entity_groups\":[{\"entity_type\":\"package\",\"source\":\"recommend\","
                + "\"candidates\":[{\"id\":\"99\",\"label\":\"乙套餐\"},{\"id\":\"11\",\"label\":\"甲套餐\"}]}],"
                + "\"session_constraints\":{\"budget\":{\"max_yuan\":200}}}";
        repository.appendTurn(7L,"s","t","推荐套餐","乙套餐、甲套餐","recommend","r",context,8);
        ArgumentCaptor<AgentMessagePO> messages = ArgumentCaptor.forClass(AgentMessagePO.class);
        verify(mapper, times(2)).insertMessage(messages.capture());
        AgentMessagePO user = messages.getAllValues().get(0), answer = messages.getAllValues().get(1);
        assertNull(user.getContextJson()); assertEquals(context, answer.getContextJson());
        when(mapper.findRecentMessages(7L,"s",16)).thenReturn(List.of(answer,user));
        assertEquals(context, repository.findSession(7L,"s",8).turns().get(0).contextJson());
    }

    @Test void malformedEntityContextIsRejectedBeforeWriting() {
        for (String value : List.of("not-json", "{}", "{\"entity_groups\":[{\"entity_type\":\"package\",\"candidates\":[{\"id\":\"-1\"}]}]}")) {
            assertThrows(IllegalArgumentException.class, () -> repository.appendTurn(7L,"s","t","u","a","recommend","r",value,8));
        }
        verify(mapper, never()).ensureSession(anyLong(), anyString());
    }
}
