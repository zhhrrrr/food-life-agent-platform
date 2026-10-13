package com.foodlife.trade.infrastructure.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.normal.repository.INormalPackageStockMessageRepository;
import com.foodlife.trade.domain.order.normal.service.NormalPackageStockMessageService;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import com.foodlife.trade.infrastructure.dao.ITradeLocalMessageMapper;
import com.foodlife.trade.infrastructure.mq.*;
import com.foodlife.trade.infrastructure.repository.NormalPackageStockMessageRepository;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AfterCommitWorkIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private IBusinessPackagePort business;
    private NormalPackageStockMessageService stockTasks;
    private RabbitTemplate rabbit;
    private TradeRabbitMqEventPublisher events;

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext();
        context.register(RefundDurabilityIntegrationTest.Config.class, NormalPackageStockMessageRepository.class);
        context.refresh();
        jdbc = context.getBean(JdbcTemplate.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        business = mock(IBusinessPackagePort.class);
        stockTasks = new NormalPackageStockMessageService(context.getBean(INormalPackageStockMessageRepository.class), business);
        rabbit = mock(RabbitTemplate.class);
        TradeRabbitMqProperties properties = new TradeRabbitMqProperties();
        properties.setEnabled(true);
        events = new TradeRabbitMqEventPublisher(properties, context.getBean(ITradeLocalMessageMapper.class),
                new ObjectMapper().findAndRegisterModules(), rabbit, context.getBean(PlatformTransactionManager.class));
        jdbc.execute("""
            CREATE TABLE trade_local_message (
              id BIGINT AUTO_INCREMENT PRIMARY KEY, message_id VARCHAR(255) UNIQUE, message_type VARCHAR(64),
              biz_type VARCHAR(64), biz_id VARCHAR(64), message_status VARCHAR(32), retry_count INT, max_retry_count INT,
              next_retry_time TIMESTAMP, content TEXT, fail_reason VARCHAR(512), create_time TIMESTAMP, update_time TIMESTAMP)
            """);
    }
    @AfterEach void close() { context.close(); }
    private String status() { return jdbc.queryForObject("SELECT message_status FROM trade_local_message", String.class); }
    private DiningOrderEntity order() {
        DiningOrderEntity order = new DiningOrderEntity();
        order.setId(1L); order.setOrderNo("ORDER1"); order.setUserId(2L); order.setPackageId(3L); order.setQuantity(1);
        return order;
    }
    @Test void stockTaskResultIsCommittedAfterOrderTransaction() {
        transaction.executeWithoutResult(status -> stockTasks.confirmSold(order()));
        assertThat(status()).isEqualTo("SUCCESS");
        verify(business).confirmPackageSold(3L, 1, "NORMAL_PACKAGE_CONFIRM_SOLD:1");
    }
    @Test void rolledBackOrderDoesNotRunOrPersistStockTask() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            stockTasks.confirmSold(order());
            throw new IllegalStateException("order commit failed");
        })).hasMessage("order commit failed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trade_local_message", Integer.class)).isZero();
        verifyNoInteractions(business);
    }
    @Test void brokerAckAndAfterCommitDatabaseWriteBothRequiredForSuccess() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        transaction.executeWithoutResult(status -> events.publish("payment", "refunded", "RF1", "payload"));
        assertThat(status()).isEqualTo("SUCCESS");
    }
    @Test void brokerNackRemainsDurablyRetryable() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        transaction.executeWithoutResult(status -> events.publish("payment", "refunded", "RF1", "payload"));
        assertThat(status()).isEqualTo("INIT");
        assertThat(jdbc.queryForObject("SELECT retry_count FROM trade_local_message", Integer.class)).isEqualTo(1);
    }
    @Test void returnedUnroutableMessageCannotBecomeSuccessfulEvenWithAck() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(invocation.getArgument(2), 312, "NO_ROUTE", "payment", "refunded"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        transaction.executeWithoutResult(status -> events.publish("payment", "refunded", "RF1", "payload"));
        assertThat(status()).isEqualTo("INIT");
    }
}
