package com.foodlife.trade.infrastructure.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.trade.domain.order.event.OrderTimeoutCloseMessage;
import com.foodlife.trade.domain.order.event.OrderTimeoutCloseResult;
import com.foodlife.trade.domain.order.event.OrderTimeoutDelayCloseService;
import com.foodlife.trade.domain.order.event.TradeMqTopics;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class TradeOrderTimeoutCloseConsumer implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TradeOrderTimeoutCloseConsumer.class);

    private final TradeRocketMqProperties properties;
    private final ObjectMapper objectMapper;
    private final OrderTimeoutDelayCloseService orderTimeoutDelayCloseService;
    private DefaultMQPushConsumer consumer;

    public TradeOrderTimeoutCloseConsumer(TradeRocketMqProperties properties,
                                          ObjectMapper objectMapper,
                                          OrderTimeoutDelayCloseService orderTimeoutDelayCloseService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.orderTimeoutDelayCloseService = orderTimeoutDelayCloseService;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            log.info("trade RocketMQ order timeout close consumer disabled");
            return;
        }
        consumer = new DefaultMQPushConsumer(properties.getOrderTimeoutConsumerGroup());
        consumer.setNamesrvAddr(properties.getNameServer());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe(TradeMqTopics.TRADE_ORDER_TOPIC, TradeMqTopics.ORDER_CANCEL_TIMEOUT);
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages, ConsumeConcurrentlyContext context) {
                for (MessageExt message : messages) {
                    if (!consumeTimeoutClose(message)) {
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.start();
        log.info("trade RocketMQ order timeout close consumer started, topic={}, group={}",
                TradeMqTopics.TRADE_ORDER_TOPIC, properties.getOrderTimeoutConsumerGroup());
    }

    @Override
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }

    private boolean consumeTimeoutClose(MessageExt message) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(body);
            OrderTimeoutCloseMessage closeMessage = objectMapper.treeToValue(root.get("payload"), OrderTimeoutCloseMessage.class);
            OrderTimeoutCloseResult result = orderTimeoutDelayCloseService.closeTimeoutOrder(closeMessage);
            log.info("consume order.cancel.timeout completed, orderId={}, canceled={}, skipped={}",
                    result.getOrderId(), result.getOrderCanceled(), result.getSkipReason());
            return true;
        } catch (Exception e) {
            log.warn("consume order.cancel.timeout failed, msgId={}, reason={}", message.getMsgId(), e.getMessage());
            return false;
        }
    }
}
