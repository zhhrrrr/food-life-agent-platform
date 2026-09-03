package com.foodlife.trade.infrastructure.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.trade.domain.order.event.OrderTimeoutCloseMessage;
import com.foodlife.trade.domain.order.event.OrderTimeoutCloseResult;
import com.foodlife.trade.domain.order.event.OrderTimeoutDelayCloseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "food.mq", name = "enabled", havingValue = "true")
public class TradeOrderTimeoutCloseListener {

    private static final Logger log = LoggerFactory.getLogger(TradeOrderTimeoutCloseListener.class);

    private final ObjectMapper objectMapper;
    private final OrderTimeoutDelayCloseService orderTimeoutDelayCloseService;

    public TradeOrderTimeoutCloseListener(ObjectMapper objectMapper,
                                          OrderTimeoutDelayCloseService orderTimeoutDelayCloseService) {
        this.objectMapper = objectMapper;
        this.orderTimeoutDelayCloseService = orderTimeoutDelayCloseService;
    }

    @RabbitListener(queues = "${food.mq.order-timeout-close-queue:food.trade.order.timeout.close.queue}")
    public void consumeTimeoutClose(Message message) throws Exception {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(body);
        JsonNode payload = root.get("payload");
        if (payload == null || payload.get("orderId") == null || payload.get("userId") == null) {
            log.info("skip non timeout-close event, messageId={}", message.getMessageProperties().getMessageId());
            return;
        }
        OrderTimeoutCloseMessage closeMessage = objectMapper.treeToValue(payload, OrderTimeoutCloseMessage.class);
        OrderTimeoutCloseResult result = orderTimeoutDelayCloseService.closeTimeoutOrder(closeMessage);
        log.info("consume order.cancel.timeout completed, orderId={}, canceled={}, skipped={}",
                result.getOrderId(), result.getOrderCanceled(), result.getSkipReason());
    }
}
