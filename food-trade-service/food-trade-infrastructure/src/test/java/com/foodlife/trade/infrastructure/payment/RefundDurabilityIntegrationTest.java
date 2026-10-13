package com.foodlife.trade.infrastructure.payment;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import com.foodlife.trade.domain.order.model.*;
import com.foodlife.trade.domain.order.payment.model.*;
import com.foodlife.trade.domain.order.payment.provider.*;
import com.foodlife.trade.domain.order.payment.repository.*;
import com.foodlife.trade.domain.order.payment.service.*;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.infrastructure.dao.*;
import com.foodlife.trade.infrastructure.repository.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring proxies, JDBC transactions and MyBatis repositories; provider I/O is fault injected. */
class RefundDurabilityIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private PaymentRefundService service;
    private RefundJournalService journal;
    private IRefundOrderRepository refunds;
    private IPaymentOrderRepository payments;
    private IOrderRepository orders;
    private IPaymentProvider provider;
    private ITradeEventPublisher events;
    private TransactionTemplate transaction;

    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.foodlife.trade.infrastructure.dao")
    @Import({PaymentOrderRepository.class, OrderRepository.class, RefundJournalService.class, PaymentRefundService.class})
    static class Config {
        @Bean DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:refund_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            MybatisConfiguration config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(config);
            return factory.getObject();
        }
        @Bean IRefundOrderRepository refunds(IRefundOrderMapper mapper) { return spy(new RefundOrderRepository(mapper)); }
        @Bean IPaymentProvider provider() {
            IPaymentProvider provider = mock(IPaymentProvider.class);
            when(provider.channel()).thenReturn("ALIPAY_SANDBOX");
            return provider;
        }
        @Bean PaymentProviderRouter router(IPaymentProvider provider) { return new PaymentProviderRouter(List.of(provider)); }
        @Bean ITradeEventPublisher events() { return mock(ITradeEventPublisher.class); }
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(PaymentRefundService.class);
        journal = context.getBean(RefundJournalService.class);
        refunds = context.getBean(IRefundOrderRepository.class);
        payments = context.getBean(IPaymentOrderRepository.class);
        orders = context.getBean(IOrderRepository.class);
        provider = context.getBean(IPaymentProvider.class);
        events = context.getBean(ITradeEventPublisher.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        jdbc.execute("""
            CREATE TABLE payment_order (
              id BIGINT AUTO_INCREMENT PRIMARY KEY, pay_order_no VARCHAR(64) UNIQUE, order_id BIGINT,
              order_no VARCHAR(64), user_id BIGINT, source VARCHAR(32), channel VARCHAR(32), pay_amount BIGINT,
              pay_status VARCHAR(32), out_trade_no VARCHAR(64), pay_time TIMESTAMP, create_time TIMESTAMP, update_time TIMESTAMP)
            """);
        jdbc.execute("""
            CREATE TABLE refund_order (
              id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_no VARCHAR(64) UNIQUE, pay_order_no VARCHAR(64) UNIQUE,
              order_id BIGINT, order_no VARCHAR(64), user_id BIGINT, source VARCHAR(32), channel VARCHAR(32),
              refund_amount BIGINT, refund_status VARCHAR(32), refund_reason VARCHAR(255), out_trade_no VARCHAR(64),
              out_refund_no VARCHAR(64), fail_reason VARCHAR(255), refund_time TIMESTAMP, create_time TIMESTAMP,
              update_time TIMESTAMP, business_completed BOOLEAN DEFAULT FALSE, recovery_count INT DEFAULT 0,
              next_retry_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
            """);
        jdbc.execute("""
            CREATE TABLE dining_order (
              id BIGINT PRIMARY KEY, order_no VARCHAR(64), request_id VARCHAR(64), request_fingerprint VARCHAR(64),
              user_id BIGINT, shop_id BIGINT, package_id BIGINT, quantity INT, total_amount BIGINT, discount_amount BIGINT,
              pay_amount BIGINT, user_coupon_id BIGINT, trade_type VARCHAR(32), order_status VARCHAR(32), use_time TIMESTAMP,
              create_time TIMESTAMP, update_time TIMESTAMP)
            """);
        jdbc.update("INSERT INTO dining_order(id,order_no,user_id,trade_type,order_status,pay_amount) VALUES(1,'ORDER_TEST',2,'NORMAL','PAID',100)");
        jdbc.update("""
            INSERT INTO payment_order(pay_order_no,order_id,order_no,user_id,source,channel,pay_amount,pay_status,out_trade_no,create_time,update_time)
            VALUES('PAY_TEST',1,'ORDER_TEST',2,'TEST','ALIPAY_SANDBOX',100,'SUCCESS','ALIPAY_TEST',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
    }

    @AfterEach void close() { context.close(); }

    private PaymentOrderEntity payment() { return payments.findByPayOrderNo("PAY_TEST"); }
    private PaymentRefundResult success() {
        PaymentRefundResult result = new PaymentRefundResult();
        result.setRefundOrderNo("RFPAY_TEST");
        result.setOutRefundNo("RFPAY_TEST");
        result.setRefundStatus("SUCCESS");
        return result;
    }
    private String refundStatus() { return jdbc.queryForObject("SELECT refund_status FROM refund_order", String.class); }
    private String paymentStatus() { return jdbc.queryForObject("SELECT pay_status FROM payment_order", String.class); }

    @Test void providerExceptionSurvivesOuterTransactionRollback() {
        when(provider.refund(any(), any())).thenThrow(new IllegalStateException("provider unavailable"));
        assertThatThrownBy(() -> transaction.execute(status ->
                service.refundPaidPayment(payment(), "TEST", "refund"))).hasMessage("provider unavailable");
        assertThat(refundStatus()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT fail_reason FROM refund_order", String.class)).isEqualTo("provider unavailable");
        assertThat(paymentStatus()).isEqualTo("SUCCESS");
    }

    @Test void rejectedResultIsPersistedAndNeverMarksPaymentRefunded() {
        PaymentRefundResult rejected = success();
        rejected.setRefundStatus("FAILED");
        rejected.setFailReason("provider rejected refund");
        when(provider.refund(any(), any())).thenReturn(rejected);
        assertThatThrownBy(() -> service.refundPaidPayment(payment(), "TEST", "refund"))
                .hasMessage("provider rejected refund");
        assertThat(refundStatus()).isEqualTo("FAILED");
        assertThat(paymentStatus()).isEqualTo("SUCCESS");
    }

    @Test void successfulRefundSurvivesLocalPaymentTransactionFailure() {
        when(provider.refund(any(), any())).thenReturn(success());
        doThrow(new IllegalStateException("outbox database failure")).when(events).publish(anyString(), anyString(), anyString(), any());
        assertThatThrownBy(() -> service.refundPaidPayment(payment(), "TEST", "refund")).hasMessage("outbox database failure");
        assertThat(refundStatus()).isEqualTo("SUCCESS");
        assertThat(paymentStatus()).isEqualTo("SUCCESS");
        reset(events);
        service.refundPaidPayment(payment(), "TEST", "refund");
        assertThat(paymentStatus()).isEqualTo("REFUNDED");
        verify(provider, times(1)).refund(any(), any());
    }

    @Test void providerSuccessThenRefundJournalCommitFailureIsRecoveredByQuery() {
        when(provider.refund(any(), any())).thenReturn(success());
        doAnswer(invocation -> {
            Object updated = invocation.callRealMethod();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("simulated commit failure"); }
            });
            return updated;
        }).doCallRealMethod().when(refunds).markRefundSuccess(anyString(), anyString(), anyString(), any());
        assertThatThrownBy(() -> service.refundPaidPayment(payment(), "TEST", "refund")).hasMessage("simulated commit failure");
        assertThat(refundStatus()).isEqualTo("FAILED");
        when(provider.queryRefund(any(), any())).thenReturn(success());
        service.refundPaidPayment(payment(), "TEST", "refund");
        assertThat(refundStatus()).isEqualTo("SUCCESS");
        assertThat(paymentStatus()).isEqualTo("REFUNDED");
        verify(provider, times(1)).refund(any(), any());
    }

    @Test void activeClaimBlocksConcurrentProviderCall() {
        RefundOrderEntity intent = journal.prepare(payment(), "TEST", "refund");
        assertThat(journal.claim(intent)).isTrue();
        assertThatThrownBy(() -> service.refundPaidPayment(payment(), "TEST", "refund")).hasMessageContaining("processing");
        verify(provider, never()).refund(any(), any());
        verify(provider, never()).queryRefund(any(), any());
    }

    @Test void processCrashIsRecoveredAfterProcessingLeaseExpires() {
        RefundOrderEntity intent = journal.prepare(payment(), "TEST", "refund");
        journal.claim(intent);
        jdbc.update("UPDATE refund_order SET update_time = ?", LocalDateTime.now().minusMinutes(5));
        when(provider.queryRefund(any(), any())).thenReturn(success());
        service.refundPaidPayment(payment(), "TEST", "refund");
        assertThat(paymentStatus()).isEqualTo("REFUNDED");
        verify(provider, never()).refund(any(), any());
    }

    @Test void durableRefundIntentPreventsOrderConsumptionAfterFailure() {
        journal.prepare(payment(), "TEST", "refund");
        jdbc.update("UPDATE refund_order SET refund_status = 'FAILED'");
        DiningOrderEntity order = orders.findOrderByIdAndUserId(1L, 2L);
        assertThatThrownBy(() -> orders.usePaidOrder(order, LocalDateTime.now(), "USE_TEST"))
                .hasMessage("order status can not use");
        assertThat(jdbc.queryForObject("SELECT order_status FROM dining_order", String.class)).isEqualTo("PAID");
    }

    @Test void completedMarkerRequiresCommittedPaymentAndBusinessStates() {
        when(provider.refund(any(), any())).thenReturn(success());
        service.refundPaidPayment(payment(), "TEST", "refund");
        assertThatThrownBy(() -> journal.businessCompleted("RFPAY_TEST"))
                .hasMessage("refund business compensation not committed");
        assertThat(jdbc.queryForObject("SELECT business_completed FROM refund_order", Boolean.class)).isFalse();
        jdbc.update("UPDATE dining_order SET order_status='REFUNDED'");
        journal.businessCompleted("RFPAY_TEST");
        assertThat(jdbc.queryForObject("SELECT business_completed FROM refund_order", Boolean.class)).isTrue();
    }

    @Test void explicitManualRetryCanReopenAnExhaustedIncompleteRequest() {
        journal.prepare(payment(), "TEST", "refund");
        jdbc.update("UPDATE refund_order SET recovery_count=10");
        assertThat(refunds.listRecoverableRefundOrders(LocalDateTime.now(), 50)).isEmpty();
        assertThat(journal.resetRecovery("RFPAY_TEST")).isTrue();
        assertThat(refunds.listRecoverableRefundOrders(LocalDateTime.now(), 50)).hasSize(1);
    }

    @Test void confirmedRefundRepairsRolledBackLateReceiptWithoutRestoringCanceledOrder() {
        journal.prepare(payment(), "ALIPAY_LATE_PAYMENT", "late receipt");
        jdbc.update("UPDATE refund_order SET refund_status='SUCCESS', refund_time=CURRENT_TIMESTAMP");
        jdbc.update("UPDATE payment_order SET pay_status='CLOSED', out_trade_no=NULL");
        jdbc.update("UPDATE dining_order SET order_status='CANCELED'");
        service.refundPaidPayment(payment(), "ALIPAY_LATE_PAYMENT", "late receipt");
        journal.businessCompleted("RFPAY_TEST");
        assertThat(paymentStatus()).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT order_status FROM dining_order", String.class)).isEqualTo("CANCELED");
        verify(provider, never()).refund(any(), any());
    }

    @Test void recoveryScansOutstandingRequestsRatherThanLatestPayments() {
        RefundOrderEntity intent = journal.prepare(payment(), "TEST", "refund");
        jdbc.update("UPDATE refund_order SET refund_status='FAILED', next_retry_time=?", LocalDateTime.now().minusDays(2));
        assertThat(refunds.listRecoverableRefundOrders(LocalDateTime.now(), 50))
                .extracting(RefundOrderEntity::getRefundOrderNo).containsExactly(intent.getRefundOrderNo());
        for (int i = 0; i < 10; i++) {
            assertThat(journal.claimRecovery(refunds.findByPayOrderNo("PAY_TEST"), LocalDateTime.now())).isTrue();
            jdbc.update("UPDATE refund_order SET next_retry_time=?", LocalDateTime.now().minusSeconds(1));
        }
        assertThat(refunds.listRecoverableRefundOrders(LocalDateTime.now(), 50)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT business_completed FROM refund_order", Boolean.class)).isFalse();
    }
}
