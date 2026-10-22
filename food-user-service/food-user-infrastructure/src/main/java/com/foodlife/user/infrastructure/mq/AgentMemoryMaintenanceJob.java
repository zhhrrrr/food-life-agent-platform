package com.foodlife.user.infrastructure.mq;

import com.foodlife.user.infrastructure.dao.IAgentMemoryMapper;
import com.foodlife.user.infrastructure.dao.po.AgentMemoryEventPO;
import com.foodlife.user.infrastructure.repository.AgentMemoryRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentMemoryMaintenanceJob {
    private final IAgentMemoryMapper mapper;
    private final AgentMemoryRepository repository;
    private final AgentMemoryOutboxPublisher publisher;

    public AgentMemoryMaintenanceJob(IAgentMemoryMapper mapper, AgentMemoryRepository repository,
                                     AgentMemoryOutboxPublisher publisher) {
        this.mapper = mapper;
        this.repository = repository;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${food.agent-memory.mq.retry-interval-ms:30000}")
    public void retryOutbox() { publisher.retryPending(); }

    @Scheduled(cron = "${food.agent-memory.expire-cron:0 20 3 * * ?}")
    public void expireEvents() {
        List<AgentMemoryEventPO> events;
        do { events = mapper.findExpiredEvents(500); events.forEach(repository::expire); } while (events.size() == 500);
    }

    @Scheduled(cron = "${food.agent-memory.reconcile-cron:0 10 4 * * ?}")
    public void reconcileEvents() {
        long afterId = 0;
        while (true) {
            List<AgentMemoryEventPO> events = mapper.findActiveEvents(afterId, 500);
            if (events.isEmpty()) return;
            events.forEach(repository::replay);
            afterId = events.get(events.size() - 1).getId();
        }
    }
}
