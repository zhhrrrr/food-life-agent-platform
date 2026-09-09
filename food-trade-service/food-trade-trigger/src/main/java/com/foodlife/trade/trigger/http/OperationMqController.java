package com.foodlife.trade.trigger.http;

import com.foodlife.trade.api.dto.OperationMqMessageListResponseDTO;
import com.foodlife.trade.api.dto.OperationMqMessageResponseDTO;
import com.foodlife.trade.api.dto.OperationMqQueueListResponseDTO;
import com.foodlife.trade.api.dto.OperationMqQueueResponseDTO;
import com.foodlife.trade.api.dto.OperationMqRepublishResponseDTO;
import com.foodlife.trade.domain.order.message.model.MqQueueBacklogEntity;
import com.foodlife.trade.domain.order.message.model.MqRepublishResult;
import com.foodlife.trade.domain.order.message.model.OperationMqMessageQuery;
import com.foodlife.trade.domain.order.message.model.TradeLocalMessageEntity;
import com.foodlife.trade.domain.order.message.service.OperationMqMessageService;
import com.foodlife.trade.types.response.Response;
import com.foodlife.trade.trigger.app.OperationAuditApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trade/operations/mq")
public class OperationMqController {

    private final OperationMqMessageService operationMqMessageService;
    private final OperationAuditApplicationService operationAuditApplicationService;

    public OperationMqController(OperationMqMessageService operationMqMessageService,
                                 OperationAuditApplicationService operationAuditApplicationService) {
        this.operationMqMessageService = operationMqMessageService;
        this.operationAuditApplicationService = operationAuditApplicationService;
    }

    @GetMapping("/messages")
    public Response<OperationMqMessageListResponseDTO> listMessages(@RequestParam(required = false) String messageStatus,
                                                                    @RequestParam(required = false) String messageType,
                                                                    @RequestParam(required = false) String bizId,
                                                                    @RequestParam(required = false) Integer limit) {
        OperationMqMessageQuery query = new OperationMqMessageQuery();
        query.setMessageStatus(messageStatus);
        query.setMessageType(messageType);
        query.setBizId(bizId);
        query.setLimit(limit);
        OperationMqMessageListResponseDTO response = new OperationMqMessageListResponseDTO();
        response.setMessages(operationMqMessageService.listMessages(query)
                .stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList()));
        return Response.success(response);
    }

    @PostMapping("/messages/{messageId}/republish")
    public Response<OperationMqRepublishResponseDTO> republish(@PathVariable String messageId) {
        MqRepublishResult result = operationMqMessageService.republish(messageId);
        OperationMqRepublishResponseDTO response = toRepublishResponse(result);
        operationAuditApplicationService.recordSuccess("MQ_MESSAGE_REPUBLISH", "MQ_MESSAGE", messageId,
                null, response, "operation republish local message");
        return Response.success(response);
    }

    @GetMapping("/queues")
    public Response<OperationMqQueueListResponseDTO> listQueues() {
        OperationMqQueueListResponseDTO response = new OperationMqQueueListResponseDTO();
        List<MqQueueBacklogEntity> queues = operationMqMessageService.listQueues();
        response.setQueues(queues.stream().map(this::toQueueResponse).collect(Collectors.toList()));
        return Response.success(response);
    }

    private OperationMqMessageResponseDTO toMessageResponse(TradeLocalMessageEntity entity) {
        OperationMqMessageResponseDTO response = new OperationMqMessageResponseDTO();
        response.setId(entity.getId());
        response.setMessageId(entity.getMessageId());
        response.setMessageType(entity.getMessageType());
        response.setBizType(entity.getBizType());
        response.setBizId(entity.getBizId());
        response.setMessageStatus(entity.getMessageStatus());
        response.setRetryCount(entity.getRetryCount());
        response.setMaxRetryCount(entity.getMaxRetryCount());
        response.setNextRetryTime(entity.getNextRetryTime());
        response.setContent(entity.getContent());
        response.setFailReason(entity.getFailReason());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private OperationMqRepublishResponseDTO toRepublishResponse(MqRepublishResult result) {
        OperationMqRepublishResponseDTO response = new OperationMqRepublishResponseDTO();
        response.setMessageId(result.getMessageId());
        response.setAccepted(result.getAccepted());
        response.setMessageStatus(result.getMessageStatus());
        response.setRemark(result.getRemark());
        return response;
    }

    private OperationMqQueueResponseDTO toQueueResponse(MqQueueBacklogEntity entity) {
        OperationMqQueueResponseDTO response = new OperationMqQueueResponseDTO();
        response.setQueueName(entity.getQueueName());
        response.setMessageCount(entity.getMessageCount());
        response.setConsumerCount(entity.getConsumerCount());
        response.setBacklogThreshold(entity.getBacklogThreshold());
        response.setBacklogAlarm(entity.getBacklogAlarm());
        return response;
    }
}
