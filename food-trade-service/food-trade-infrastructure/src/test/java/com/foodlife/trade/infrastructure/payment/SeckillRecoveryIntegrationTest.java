package com.foodlife.trade.infrastructure.payment;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import com.foodlife.trade.domain.order.factory.OrderFactory;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillStockRepository;
import com.foodlife.trade.domain.order.seckill.service.SeckillOrderService;
import com.foodlife.trade.infrastructure.repository.SeckillRepository;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeckillRecoveryIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private SeckillRepository repository;
    private ISeckillStockRepository stock;
    private SeckillOrderService service;

    @Configuration
    @MapperScan("com.foodlife.trade.infrastructure.dao")
    @Import(SeckillRepository.class)
    static class Config {
        @Bean DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:seckill_recovery_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            return ds;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            MybatisConfiguration config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(config);
            return factory.getObject();
        }
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        DataSource ds = context.getBean(DataSource.class);
        jdbc = new JdbcTemplate(ds);
        repository = context.getBean(SeckillRepository.class);
        stock = mock(ISeckillStockRepository.class);
        service = new SeckillOrderService(repository, stock, mock(IOrderRepository.class),
                mock(IBusinessPackagePort.class), mock(OrderFactory.class), mock(ITradeEventPublisher.class),
                new TransactionTemplate(new DataSourceTransactionManager(ds)));
        jdbc.execute("""
            CREATE TABLE seckill_order_request (
              id BIGINT AUTO_INCREMENT PRIMARY KEY, request_no VARCHAR(64), client_request_id VARCHAR(64),
              request_fingerprint VARCHAR(64), expected_unit_price BIGINT, user_id BIGINT, activity_id BIGINT,
              package_id BIGINT, quantity INT, order_id BIGINT, order_no VARCHAR(64), request_status VARCHAR(32),
              fail_reason VARCHAR(512), create_time TIMESTAMP, update_time TIMESTAMP);
            CREATE TABLE trade_local_message (
              id BIGINT AUTO_INCREMENT PRIMARY KEY, message_id VARCHAR(64), message_type VARCHAR(64),
              biz_type VARCHAR(64), biz_id VARCHAR(64), message_status VARCHAR(32), retry_count INT,
              max_retry_count INT, next_retry_time TIMESTAMP, content VARCHAR(4096), fail_reason VARCHAR(512),
              create_time TIMESTAMP, update_time TIMESTAMP);
            INSERT INTO seckill_order_request(request_no,user_id,activity_id,package_id,quantity,request_status,create_time,update_time)
              VALUES ('REQ1',7,3,1,1,'INIT',DATEADD('MINUTE',-10,CURRENT_TIMESTAMP),CURRENT_TIMESTAMP);
            """);
    }

    @AfterEach void close() { context.close(); }

    private String status() {
        return jdbc.queryForObject("SELECT request_status FROM seckill_order_request WHERE request_no='REQ1'", String.class);
    }

    @Test void failedRequestCannotReturnToProcessing() {
        jdbc.update("UPDATE seckill_order_request SET request_status='FAILED'");
        assertThat(repository.markSeckillOrderRequestProcessing("REQ1")).isFalse();
        assertThat(status()).isEqualTo("FAILED");
    }

    @Test void staleCreateMessageIsRetiredForAFailedRequest() {
        jdbc.update("UPDATE seckill_order_request SET request_status='FAILED'");
        jdbc.update("INSERT INTO trade_local_message(message_type,biz_id,message_status,retry_count,max_retry_count,next_retry_time) VALUES ('SECKILL_ORDER_CREATE','REQ1','INIT',0,5,?)",
                java.time.LocalDateTime.now().minusMinutes(1));
        assertThat(service.processPendingOrderRequests(10).getFailedCount()).isEqualTo(1);
        assertThat(status()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT message_status FROM trade_local_message", String.class)).isEqualTo("FAILED");
    }

    @Test void lostRedisAcknowledgmentRollsBackCancellationAndRetriesSameRelease() {
        AtomicInteger releases = new AtomicInteger();
        HashSet<String> receipts = new HashSet<>();
        doAnswer(call -> { releases.incrementAndGet(); throw new IllegalStateException("lost Redis acknowledgment"); })
                .when(stock).releaseActivityStock(anyLong(), anyLong());
        doAnswer(call -> {
            if (receipts.add(call.getArgument(2))) {
                releases.incrementAndGet();
                throw new IllegalStateException("lost Redis acknowledgment");
            }
            return null;
        }).when(stock).releaseActivityStock(anyLong(), anyLong(), anyString());
        assertThatThrownBy(() -> service.recoverOrderRequests(10)).isInstanceOf(IllegalStateException.class);
        assertThat(status()).isEqualTo("INIT");
        assertThat(service.recoverOrderRequests(10).getCanceledRequestCount()).isEqualTo(1);
        assertThat(status()).isEqualTo("FAILED");
        assertThat(releases.get()).isEqualTo(1);
        assertThat(receipts).hasSize(1);
    }
}
