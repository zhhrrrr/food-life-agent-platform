package com.foodlife.user.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.user.infrastructure.dao.IAgentMemoryMapper;
import com.foodlife.user.infrastructure.dao.po.AgentMemoryEventPO;
import com.foodlife.user.infrastructure.mq.*;
import com.foodlife.user.infrastructure.repository.AgentMemoryRepository;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.*;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MemoryDurabilityTest {
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private IAgentMemoryMapper mapper;
    public static LocalDateTime utcNow() { return LocalDateTime.now(ZoneOffset.UTC); }

    @BeforeEach void setup() throws Exception {
        source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE ALIAS IF NOT EXISTS UTC_TIMESTAMP FOR 'com.foodlife.user.infrastructure.MemoryDurabilityTest.utcNow'");
        jdbc.execute("CREATE TABLE user_memory_local_message (id BIGINT AUTO_INCREMENT PRIMARY KEY, message_id VARCHAR(64), event_type VARCHAR(32), event_id VARCHAR(64), content VARCHAR(4000), message_status VARCHAR(32), retry_count INT DEFAULT 0, max_retry_count INT DEFAULT 8, next_retry_time TIMESTAMP, fail_reason VARCHAR(512), create_time TIMESTAMP, update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE agent_user_memory_event (id BIGINT PRIMARY KEY, event_id VARCHAR(64), user_id BIGINT, event_type VARCHAR(64), content VARCHAR(1000), entity_type VARCHAR(32), entity_id VARCHAR(64), source_turn_id VARCHAR(64), status VARCHAR(32), occurred_at TIMESTAMP, expires_at TIMESTAMP, version BIGINT, update_time TIMESTAMP)");
        Configuration config = new Configuration(); config.setMapUnderscoreToCamelCase(true); config.addMapper(IAgentMemoryMapper.class);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(config);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(IAgentMemoryMapper.class);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @Test void outboxSuccessIsCommittedBeforeAfterCommitReturns() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(call -> { CorrelationData correlation = call.getArgument(3); correlation.getFuture().complete(new CorrelationData.Confirm(true, null)); return null; })
                .when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        AgentMemoryOutboxPublisher publisher = new AgentMemoryOutboxPublisher(mapper, new AgentMemoryMqProperties(), rabbit,
                new DataSourceTransactionManager(source));
        transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO user_memory_local_message(message_id,event_type,event_id,content,message_status) VALUES('m','UPSERT','e','{}','INIT')");
            publisher.publishAfterCommit("m");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try (Connection connection = source.getConnection(); var query = connection.createStatement(); var result = query.executeQuery("SELECT message_status FROM user_memory_local_message WHERE message_id='m'")) {
                        assertTrue(result.next()); assertEquals("SUCCESS", result.getString(1));
                    } catch (java.sql.SQLException e) { throw new RuntimeException(e); }
                }
            });
        });
    }

    @Test void staleExpiryScanCannotExpireRenewedMemory() {
        jdbc.update("INSERT INTO agent_user_memory_event(id,event_id,user_id,status,expires_at,version) VALUES(1,'e',7,'ACTIVE',?,2)", utcNow().plusDays(2));
        AgentMemoryEventPO stale = new AgentMemoryEventPO(); stale.setId(1L); stale.setEventId("e"); stale.setUserId(7L); stale.setVersion(1L);
        new AgentMemoryRepository(mapper, new ObjectMapper().findAndRegisterModules(), mock(AgentMemoryOutboxPublisher.class)).expire(stale);
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM agent_user_memory_event WHERE id=1", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_memory_local_message", Integer.class));
    }
}
