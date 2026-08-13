package com.foodlife.trade.domain.order.normal.service;

import com.foodlife.trade.domain.order.message.constant.LocalMessageStatusConstants;
import com.foodlife.trade.domain.order.message.model.TradeLocalMessageEntity;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.normal.constant.NormalPackageStockMessageConstants;
import com.foodlife.trade.domain.order.normal.model.NormalPackageStockMessageContent;
import com.foodlife.trade.domain.order.normal.model.NormalPackageStockSyncResult;
import com.foodlife.trade.domain.order.normal.repository.INormalPackageStockMessageRepository;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NormalPackageStockMessageService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_RETRY_COUNT = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 120;

    private final INormalPackageStockMessageRepository messageRepository;
    private final IBusinessPackagePort businessPackagePort;

    public NormalPackageStockMessageService(INormalPackageStockMessageRepository messageRepository,
                                            IBusinessPackagePort businessPackagePort) {
        this.messageRepository = messageRepository;
        this.businessPackagePort = businessPackagePort;
    }

    public void releaseStock(DiningOrderEntity order) {
        createAndExecute(NormalPackageStockMessageConstants.RELEASE_STOCK, order);
    }

    public void confirmSold(DiningOrderEntity order) {
        createAndExecute(NormalPackageStockMessageConstants.CONFIRM_SOLD, order);
    }

    public void rollbackSoldAndReleaseStock(DiningOrderEntity order) {
        createAndExecute(NormalPackageStockMessageConstants.ROLLBACK_SOLD, order);
        createAndExecute(NormalPackageStockMessageConstants.RELEASE_STOCK, order);
    }

    public NormalPackageStockSyncResult compensatePendingMessages(Integer limit) {
        LocalDateTime now = LocalDateTime.now();
        int normalizedLimit = normalizeLimit(limit);
        NormalPackageStockSyncResult result = new NormalPackageStockSyncResult();
        result.setCompensateTime(now);

        List<TradeLocalMessageEntity> stuckMessages = messageRepository.queryProcessingMessages(
                now.minusSeconds(PROCESSING_TIMEOUT_SECONDS),
                normalizedLimit
        );
        for (TradeLocalMessageEntity message : stuckMessages) {
            if (messageRepository.recoverProcessingMessage(message.getId(), now)) {
                result.setRetryCount(result.getRetryCount() + 1);
            }
        }

        List<TradeLocalMessageEntity> messages = messageRepository.queryPendingMessages(now, normalizedLimit);
        result.setScannedMessageCount(messages.size());
        for (TradeLocalMessageEntity message : messages) {
            if (!messageRepository.markMessageProcessing(message.getId())) {
                continue;
            }
            processSingleMessage(message, result);
        }
        return result;
    }

    private void createAndExecute(String messageType, DiningOrderEntity order) {
        LocalDateTime now = LocalDateTime.now();
        String bizId = String.valueOf(order.getId());
        String messageId = buildMessageId(messageType, bizId);
        TradeLocalMessageEntity existed = messageRepository.queryMessageByMessageId(messageId);
        TradeLocalMessageEntity message = existed == null
                ? messageRepository.saveInitMessage(messageType, bizId, buildContent(messageType, order), now)
                : existed;

        if (LocalMessageStatusConstants.SUCCESS.equals(message.getMessageStatus())) {
            return;
        }
        if (!LocalMessageStatusConstants.INIT.equals(message.getMessageStatus())) {
            return;
        }
        if (!messageRepository.markMessageProcessing(message.getId())) {
            return;
        }

        NormalPackageStockSyncResult ignored = new NormalPackageStockSyncResult();
        processSingleMessage(message, ignored);
    }

    private void processSingleMessage(TradeLocalMessageEntity message, NormalPackageStockSyncResult result) {
        try {
            NormalPackageStockMessageContent content = parseContent(message.getContent());
            executeMessage(content, message.getMessageId());
            messageRepository.markMessageSuccess(message.getId());
            result.setSuccessCount(result.getSuccessCount() + 1);
        } catch (Exception e) {
            if (message.getRetryCount() != null && message.getRetryCount() >= MAX_RETRY_COUNT) {
                messageRepository.markMessageFailed(message.getId(), e.getMessage());
                result.setFailedCount(result.getFailedCount() + 1);
                return;
            }
            messageRepository.markMessageRetry(message.getId(), e.getMessage(), LocalDateTime.now().plusSeconds(30));
            result.setRetryCount(result.getRetryCount() + 1);
        }
    }

    private void executeMessage(NormalPackageStockMessageContent content, String operationId) {
        if (NormalPackageStockMessageConstants.RELEASE_STOCK.equals(content.getActionType())) {
            businessPackagePort.releasePackageStock(content.getPackageId(), content.getQuantity(), operationId);
            return;
        }
        if (NormalPackageStockMessageConstants.CONFIRM_SOLD.equals(content.getActionType())) {
            businessPackagePort.confirmPackageSold(content.getPackageId(), content.getQuantity(), operationId);
            return;
        }
        if (NormalPackageStockMessageConstants.ROLLBACK_SOLD.equals(content.getActionType())) {
            businessPackagePort.rollbackPackageSold(content.getPackageId(), content.getQuantity(), operationId);
            return;
        }
        throw new IllegalArgumentException("unsupported normal package stock action");
    }

    private String buildMessageId(String messageType, String bizId) {
        return messageType + ":" + bizId;
    }

    private String buildContent(String messageType, DiningOrderEntity order) {
        return "orderId=" + order.getId()
                + ";orderNo=" + readString(order.getOrderNo())
                + ";userId=" + order.getUserId()
                + ";packageId=" + order.getPackageId()
                + ";quantity=" + order.getQuantity()
                + ";actionType=" + messageType;
    }

    private NormalPackageStockMessageContent parseContent(String content) {
        NormalPackageStockMessageContent result = new NormalPackageStockMessageContent();
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("message content empty");
        }
        String[] pairs = content.split(";");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            applyContentField(result, keyValue[0], keyValue[1]);
        }
        if (result.getPackageId() == null || result.getQuantity() == null || result.getActionType() == null) {
            throw new IllegalArgumentException("message content invalid");
        }
        return result;
    }

    private void applyContentField(NormalPackageStockMessageContent result, String key, String value) {
        if ("orderId".equals(key)) {
            result.setOrderId(parseLong(value));
        } else if ("orderNo".equals(key)) {
            result.setOrderNo(value);
        } else if ("userId".equals(key)) {
            result.setUserId(parseLong(value));
        } else if ("packageId".equals(key)) {
            result.setPackageId(parseLong(value));
        } else if ("quantity".equals(key)) {
            result.setQuantity(parseInteger(value));
        } else if ("actionType".equals(key)) {
            result.setActionType(value);
        }
    }

    private Long parseLong(String value) {
        return value == null || value.trim().isEmpty() ? null : Long.valueOf(value);
    }

    private Integer parseInteger(String value) {
        return value == null || value.trim().isEmpty() ? null : Integer.valueOf(value);
    }

    private String readString(String value) {
        return value == null ? "" : value;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
