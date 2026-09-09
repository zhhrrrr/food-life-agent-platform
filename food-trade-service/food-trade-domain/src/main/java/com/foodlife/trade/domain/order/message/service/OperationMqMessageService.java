package com.foodlife.trade.domain.order.message.service;

import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import com.foodlife.trade.domain.order.message.model.MqQueueBacklogEntity;
import com.foodlife.trade.domain.order.message.model.MqRepublishResult;
import com.foodlife.trade.domain.order.message.model.OperationMqMessageQuery;
import com.foodlife.trade.domain.order.message.model.TradeLocalMessageEntity;
import com.foodlife.trade.domain.order.message.repository.IMqQueueInspector;
import com.foodlife.trade.domain.order.message.repository.IOperationMqMessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationMqMessageService {

    private final IOperationMqMessageRepository operationMqMessageRepository;
    private final IMqQueueInspector mqQueueInspector;
    private final ITradeEventPublisher tradeEventPublisher;

    public OperationMqMessageService(IOperationMqMessageRepository operationMqMessageRepository,
                                     IMqQueueInspector mqQueueInspector,
                                     ITradeEventPublisher tradeEventPublisher) {
        this.operationMqMessageRepository = operationMqMessageRepository;
        this.mqQueueInspector = mqQueueInspector;
        this.tradeEventPublisher = tradeEventPublisher;
    }

    public List<TradeLocalMessageEntity> listMessages(OperationMqMessageQuery query) {
        return operationMqMessageRepository.listMessages(query);
    }

    public MqRepublishResult republish(String messageId) {
        MqRepublishResult result = new MqRepublishResult();
        result.setMessageId(messageId);
        if (messageId == null || messageId.trim().isEmpty()) {
            result.setAccepted(false);
            result.setMessageStatus("REJECTED");
            result.setRemark("messageId required");
            return result;
        }
        boolean accepted = tradeEventPublisher.republishMessage(messageId.trim());
        result.setAccepted(accepted);
        result.setMessageStatus(accepted ? "ACCEPTED" : "REJECTED");
        result.setRemark(accepted ? "message republish submitted" : "message can not republish");
        return result;
    }

    public List<MqQueueBacklogEntity> listQueues() {
        return mqQueueInspector.listTradeQueues();
    }
}
